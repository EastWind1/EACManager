package pers.eastwind.billmanager.servicebill.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import pers.eastwind.billmanager.common.model.ActionsResult;
import pers.eastwind.billmanager.security.model.AuthorityUser;
import pers.eastwind.billmanager.security.service.AuthenticateService;
import pers.eastwind.billmanager.security.service.AuthorizeService;
import pers.eastwind.billmanager.servicebill.model.AssigneeDTO;
import pers.eastwind.billmanager.servicebill.model.ServiceBillDTO;
import pers.eastwind.billmanager.servicebill.model.ServiceBillDetailDTO;
import pers.eastwind.billmanager.servicebill.model.ServiceBillState;
import pers.eastwind.billmanager.user.model.UserDTO;
import pers.eastwind.billmanager.user.service.UserService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 服务单关键动作的并发集成测试。
 *
 * <p>这里刻意不使用事务回滚：并发场景必须让每个线程各自提交事务，否则其它线程看不到数据，
 * 也测不出乐观锁与唯一约束的竞争。因此用例开始时按项目名前缀清理残留，结束时删除本用例创建的单据。</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
class ServiceBillConcurrencyTest {

    private static final String PROJECT_PREFIX = "并发测试";
    private static final int THREADS = 8;

    @Autowired
    private ServiceBillActionService actionService;

    @Autowired
    private ServiceBillQueryService queryService;

    @Autowired
    private UserService userService;

    @Autowired
    private AuthenticateService authenticateService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CacheManager cacheManager;

    private final Set<Integer> createdBillIds = ConcurrentHashMap.newKeySet();

    private AuthorityUser admin;

    @BeforeEach
    void setUp() {
        cleanUpByProjectPrefix();
        createdBillIds.clear();
        admin = authenticateService.loadUserByUsername(ensureAdmin().getUsername());
    }

    @AfterEach
    void tearDown() {
        Set<Integer> ids = new LinkedHashSet<>(createdBillIds);
        ids.addAll(jdbcTemplate.queryForList(
                "select id from service_bill where project_name like ?", Integer.class, PROJECT_PREFIX + "%"));
        cleanUp(ids);
        createdBillIds.clear();
    }

    @Test
    @DisplayName("并发对同一张新建单执行处理：只有一个线程成功")
    void onlyOneProcessSucceeds() {
        ServiceBillDTO bill = createBill();

        List<Outcome<ActionsResult<Integer, Void>>> outcomes = runConcurrently(
                THREADS, () -> actionService.process(List.of(bill.getId())));

        assertNoUnexpectedError(outcomes);
        assertEquals(1, sumSuccess(outcomes), "状态机只允许一次 CREATED -> PROCESSING");
        assertEquals(ServiceBillState.PROCESSING, loadBill(bill.getId()).getState());
    }

    @Test
    @DisplayName("并发处理多张互不相关的单据：全部成功")
    void processDifferentBillsConcurrently() {
        List<ServiceBillDTO> bills = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            bills.add(createBill());
        }
        List<Callable<ActionsResult<Integer, Void>>> actions = bills.stream()
                .map(bill -> (Callable<ActionsResult<Integer, Void>>) () -> actionService.process(List.of(bill.getId())))
                .toList();

        List<Outcome<ActionsResult<Integer, Void>>> outcomes = runConcurrently(actions);

        assertNoUnexpectedError(outcomes);
        assertEquals(THREADS, sumSuccess(outcomes), "不同单据之间不应互相干扰");
        for (ServiceBillDTO bill : bills) {
            assertEquals(ServiceBillState.PROCESSING, loadBill(bill.getId()).getState());
        }
    }

    @Test
    @DisplayName("并发删除同一张单据：只有一次成功且不留下负责人和明细")
    void onlyOneDeleteSucceeds() {
        ServiceBillDTO bill = createBill();
        assertEquals(1, countAssignees(bill.getId()));
        assertEquals(1, countDetails(bill.getId()));

        List<Outcome<ActionsResult<Integer, Void>>> outcomes = runConcurrently(
                THREADS, () -> actionService.delete(List.of(bill.getId())));

        assertNoUnexpectedError(outcomes);
        assertEquals(1, sumSuccess(outcomes), "同一张单据只能被删除一次");
        assertEquals(0, countBill(bill.getId()));
        assertEquals(0, countAssignees(bill.getId()), "删除后不能残留负责人关联");
        assertEquals(0, countDetails(bill.getId()), "删除后不能残留明细");
    }

    @Test
    @DisplayName("并发删除与处理同一张单据：两者互斥，结果保持一致")
    void deleteAndProcessAreMutuallyExclusive() {
        ServiceBillDTO bill = createBill();
        Integer id = bill.getId();

        List<Outcome<ActionsResult<Integer, Void>>> outcomes = runConcurrently(List.of(
                () -> actionService.process(List.of(id)),
                () -> actionService.delete(List.of(id))));

        assertNoUnexpectedError(outcomes);
        assertEquals(1, sumSuccess(outcomes), "删除与状态推进只能有一个成功");

        if (countBill(id) == 0) {
            assertEquals(0, countAssignees(id), "删除成功后不能残留负责人关联");
            assertEquals(0, countDetails(id), "删除成功后不能残留明细");
        } else {
            assertEquals(ServiceBillState.PROCESSING, loadBill(id).getState());
            assertEquals(1, countAssignees(id), "处理成功后负责人应保持不变");
            assertEquals(1, countDetails(id), "处理成功后明细应保持不变");
        }
    }

    @Test
    @DisplayName("并发重复指派同一组负责人：最终关联等于目标集合且没有重复行")
    void concurrentAssignKeepsSingleCopyOfEachAssignee() {
        List<Integer> targets = queryService.findAssignees().stream()
                .map(AssigneeDTO::getId)
                .limit(2)
                .toList();
        assertFalse(targets.isEmpty(), "库里至少需要一个启用用户");
        ServiceBillDTO bill = createBill();

        List<Outcome<ActionsResult<Integer, Void>>> outcomes = runConcurrently(
                THREADS, () -> actionService.assign(List.of(bill.getId()), targets));

        assertNoUnexpectedError(outcomes);
        assertTrue(sumSuccess(outcomes) >= 1, "至少有一次指派应成功");

        List<Integer> assigneeIds = jdbcTemplate.queryForList(
                "select user_id from service_bill_assignee where service_bill_id = ? order by user_id",
                Integer.class, bill.getId());
        assertEquals(targets.size(), assigneeIds.size(), "唯一约束下同一用户只能出现一次");
        assertEquals(new TreeSet<>(targets), new TreeSet<>(assigneeIds), "最终负责人应等于目标集合");
    }

    @Test
    @DisplayName("并发创建服务单：全部落库且单号互不重复")
    void concurrentCreateGeneratesDistinctNumbers() {
        List<Outcome<ServiceBillDTO>> outcomes = runConcurrently(THREADS, this::createBill);

        assertNoUnexpectedError(outcomes);
        List<ServiceBillDTO> bills = outcomes.stream().map(Outcome::value).toList();
        assertEquals(THREADS, bills.stream().map(ServiceBillDTO::getId).distinct().count());
        assertEquals(THREADS, bills.stream().map(ServiceBillDTO::getNumber).distinct().count(),
                "并发生成的单号不能重复");
        for (ServiceBillDTO bill : bills) {
            assertEquals(ServiceBillState.CREATED, loadBill(bill.getId()).getState());
            assertEquals(1, countAssignees(bill.getId()), "创建者应自动成为负责人");
        }
    }

    @Test
    @DisplayName("并发使用同一个手填单号创建：只成功一张")
    void concurrentCreateWithSameNumberOnlySucceedsOnce() {
        String number = "CT" + System.nanoTime();

        List<Outcome<ServiceBillDTO>> outcomes = runConcurrently(THREADS, () -> createBill(number));

        long succeeded = outcomes.stream().filter(outcome -> outcome.error() == null).count();
        assertEquals(1, succeeded, "重复单号必须在数据库层面被拒绝");
        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from service_bill where number = ?", Long.class, number));
    }

    @Test
    @DisplayName("并发取消处理与标记处理完成：只有一个成功且日期与状态一致")
    void cancelProcessAndProcessedAreMutuallyExclusive() {
        ServiceBillDTO bill = createBill();
        Integer id = bill.getId();
        assertEquals(1, asAdmin(() -> actionService.process(List.of(id))).getSuccessCount());

        List<Outcome<ActionsResult<Integer, Void>>> outcomes = runConcurrently(List.of(
                () -> actionService.cancelProcess(List.of(id)),
                () -> actionService.processed(List.of(id), Instant.now())));

        assertNoUnexpectedError(outcomes);
        assertEquals(1, sumSuccess(outcomes), "PROCESSING 只能被推进或回退一次");

        ServiceBillDTO result = loadBill(id);
        assertTrue(result.getState() == ServiceBillState.CREATED || result.getState() == ServiceBillState.PROCESSED,
                "最终状态应取决于成功的那次操作，实际为 " + result.getState());
        if (result.getState() == ServiceBillState.CREATED) {
            assertNull(result.getProcessedDate(), "取消处理后必须清空完工时间");
        } else {
            assertNotNull(result.getProcessedDate(), "处理完成后必须写入完工时间");
        }
    }

    /**
     * 同一动作并发执行；每个线程独立设置登录上下文，结束后清理，避免 ThreadLocal 串用。
     */
    private <T> List<Outcome<T>> runConcurrently(int threads, Callable<T> action) {
        List<Callable<T>> actions = new ArrayList<>(threads);
        for (int i = 0; i < threads; i++) {
            actions.add(action);
        }
        return runConcurrently(actions);
    }

    private <T> List<Outcome<T>> runConcurrently(List<Callable<T>> actions) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Outcome<T>>> futures = new ArrayList<>(actions.size());
            for (Callable<T> action : actions) {
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        authenticateAsAdmin();
                        return new Outcome<>(action.call(), null);
                    } catch (Throwable error) {
                        return new Outcome<>(null, error);
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }));
            }
            start.countDown();

            List<Outcome<T>> outcomes = new ArrayList<>(actions.size());
            for (Future<Outcome<T>> future : futures) {
                outcomes.add(future.get(60, TimeUnit.SECONDS));
            }
            return outcomes;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("并发测试被中断", e);
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            throw new AssertionError("并发执行失败", e);
        }
    }

    private ServiceBillDTO createBill() {
        return createBill(null);
    }

    private ServiceBillDTO createBill(String number) {
        ServiceBillDTO bill = new ServiceBillDTO();
        bill.setState(ServiceBillState.CREATED);
        bill.setProjectName(PROJECT_PREFIX + "-" + System.nanoTime());
        bill.setProjectAddress("并发测试地址");
        bill.setTotalAmount(new BigDecimal("100.00"));
        bill.setOrderDate(Instant.now());

        ServiceBillDetailDTO detail = new ServiceBillDetailDTO();
        detail.setDevice("并发测试设备");
        detail.setQuantity(BigDecimal.ONE);
        detail.setUnitPrice(new BigDecimal("100.00"));
        detail.setSubtotal(new BigDecimal("100.00"));
        bill.setDetails(List.of(detail));

        if (number != null) {
            bill.setNumber(number);
        }

        ServiceBillDTO created = asAdmin(() -> actionService.create(bill));
        createdBillIds.add(created.getId());
        return created;
    }

    private ServiceBillDTO loadBill(Integer id) {
        return asAdmin(() -> queryService.findFullById(id));
    }

    private UserDTO ensureAdmin() {
        if (userService.isInitializationRequired()) {
            UserDTO dto = new UserDTO();
            dto.setUsername("testAdmin");
            dto.setPassword("Admin12345");
            dto.setName("测试管理员");
            return userService.initialize(dto);
        }
        return userService.findEnabledUsersByRole(AuthorizeService.ADMIN_ROLE_CODE).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("数据库中没有启用的管理员用户"));
    }

    private void authenticateAsAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, "", admin.getAuthorities()));
    }

    private <T> T asAdmin(Supplier<T> action) {
        authenticateAsAdmin();
        try {
            return action.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private long countBill(Integer id) {
        return jdbcTemplate.queryForObject("select count(*) from service_bill where id = ?", Long.class, id);
    }

    private long countAssignees(Integer id) {
        return jdbcTemplate.queryForObject(
                "select count(*) from service_bill_assignee where service_bill_id = ?", Long.class, id);
    }

    private long countDetails(Integer id) {
        return jdbcTemplate.queryForObject(
                "select count(*) from service_bill_detail where service_bill_id = ?", Long.class, id);
    }

    private void cleanUpByProjectPrefix() {
        List<Integer> ids = jdbcTemplate.queryForList(
                "select id from service_bill where project_name like ?", Integer.class, PROJECT_PREFIX + "%");
        cleanUp(new LinkedHashSet<>(ids));
    }

    private void cleanUp(Set<Integer> ids) {
        if (ids.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        Object[] args = ids.toArray();
        jdbcTemplate.update("delete from service_bill_attach_relation where service_bill_id in (" + placeholders + ")", args);
        jdbcTemplate.update("delete from service_bill_assignee where service_bill_id in (" + placeholders + ")", args);
        jdbcTemplate.update("delete from service_bill_detail where service_bill_id in (" + placeholders + ")", args);
        jdbcTemplate.update("delete from service_bill where id in (" + placeholders + ")", args);
        evictBillCaches();
    }

    private void evictBillCaches() {
        for (String name : List.of(ServiceBillQueryService.QUERY_CACHE, ServiceBillStatisticService.AMOUNT_STAT_CACHE)) {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.invalidate();
            }
        }
    }

    private static int sumSuccess(List<Outcome<ActionsResult<Integer, Void>>> outcomes) {
        return outcomes.stream().mapToInt(outcome -> outcome.value().getSuccessCount()).sum();
    }

    private static void assertNoUnexpectedError(List<? extends Outcome<?>> outcomes) {
        for (Outcome<?> outcome : outcomes) {
            if (outcome.error() != null) {
                fail("并发调用抛出了未预期的异常", outcome.error());
            }
        }
    }

    /**
     * 并发结果：正常返回记 value，抛出的异常记 error，由用例决定如何断言。
     */
    private record Outcome<T>(T value, Throwable error) {
    }
}

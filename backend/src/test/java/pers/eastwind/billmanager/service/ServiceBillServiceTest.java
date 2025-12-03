package pers.eastwind.billmanager.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import pers.eastwind.billmanager.servicebill.model.ServiceBillState;
import pers.eastwind.billmanager.common.model.ActionsResult;
import pers.eastwind.billmanager.servicebill.model.ServiceBillDTO;
import pers.eastwind.billmanager.servicebill.model.ServiceBillDetailDTO;
import pers.eastwind.billmanager.servicebill.model.ServiceBillQueryParam;
import pers.eastwind.billmanager.servicebill.repository.ServiceBillRepository;
import pers.eastwind.billmanager.servicebill.service.ServiceBillService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ServiceBillServiceTest {

    @Autowired
    private ServiceBillService serviceBillService;

    @Autowired
    private ServiceBillRepository serviceBillRepository;

    private ServiceBillDTO testServiceBillDTO;

    @BeforeEach
    void setUp() {
        testServiceBillDTO = new ServiceBillDTO();
        testServiceBillDTO.setState(ServiceBillState.CREATED);
        testServiceBillDTO.setProjectName("集成测试项目");
        testServiceBillDTO.setProjectAddress("测试地址");
        testServiceBillDTO.setTotalAmount(new BigDecimal("5000.00"));
        testServiceBillDTO.setOrderDate(Instant.now());

        ServiceBillDetailDTO detailDTO = new ServiceBillDetailDTO();
        detailDTO.setDevice("测试设备");
        detailDTO.setUnitPrice(new BigDecimal("1000.00"));
        detailDTO.setQuantity(2);
        detailDTO.setSubtotal(new BigDecimal("5000.00"));

        testServiceBillDTO.setDetails(List.of(detailDTO));
        testServiceBillDTO.setAttachments(new ArrayList<>());
    }

    @Test
    @DisplayName("测试创建服务单据")
    void shouldCreateServiceBillSuccessfully() {
        // When
        ServiceBillDTO createdBill = serviceBillService.create(testServiceBillDTO);

        // Then
        assertNotNull(createdBill.getId());
        assertEquals(testServiceBillDTO.getNumber(), createdBill.getNumber());
        assertEquals(testServiceBillDTO.getProjectName(), createdBill.getProjectName());
        assertEquals(ServiceBillState.CREATED, createdBill.getState());
        assertEquals(1, createdBill.getDetails().size());
        assertEquals("测试设备", createdBill.getDetails().getFirst().getDevice());
    }

    @Test
    @DisplayName("测试根据ID查找服务单据")
    void shouldFindServiceBillById() {
        // Given
        ServiceBillDTO createdBill = serviceBillService.create(testServiceBillDTO);

        // When
        ServiceBillDTO foundBill = serviceBillService.findById(createdBill.getId());

        // Then
        assertNotNull(foundBill);
        assertEquals(createdBill.getId(), foundBill.getId());
        assertEquals(createdBill.getNumber(), foundBill.getNumber());
        assertEquals(createdBill.getProjectName(), foundBill.getProjectName());
    }

    @Test
    @DisplayName("测试更新服务单据")
    void shouldUpdateServiceBillSuccessfully() {
        // Given
        ServiceBillDTO createdBill = serviceBillService.create(testServiceBillDTO);
        createdBill.setProjectName("更新后的项目名称");
        createdBill.setTotalAmount(new BigDecimal("6000.00"));

        ServiceBillDetailDTO detailDTO = new ServiceBillDetailDTO();
        detailDTO.setDevice("更新后的设备");
        detailDTO.setUnitPrice(new BigDecimal("2000.00"));
        detailDTO.setQuantity(3);
        detailDTO.setSubtotal(new BigDecimal("6000.00"));
        createdBill.setDetails(List.of(detailDTO));

        // When
        ServiceBillDTO updatedBill = serviceBillService.update(createdBill);

        // Then
        assertEquals("更新后的项目名称", updatedBill.getProjectName());
        assertEquals(new BigDecimal("6000.00"), updatedBill.getTotalAmount());
        assertEquals(1, updatedBill.getDetails().size());
        assertEquals("更新后的设备", updatedBill.getDetails().getFirst().getDevice());
    }

    @Test
    @DisplayName("测试根据条件查询服务单据")
    void shouldFindByParamSuccessfully() {
        // Given
        serviceBillService.create(testServiceBillDTO);

        ServiceBillDTO anotherBill = new ServiceBillDTO();
        anotherBill.setNumber("SIT-002");
        anotherBill.setState(ServiceBillState.PROCESSING);
        anotherBill.setProjectName("另一个测试项目");
        anotherBill.setProjectAddress("另一个测试地址");
        anotherBill.setTotalAmount(new BigDecimal("3000.00"));
        anotherBill.setOrderDate(Instant.now());
        anotherBill.setDetails(new ArrayList<>());
        anotherBill.setAttachments(new ArrayList<>());
        serviceBillService.create(anotherBill);

        // When
        ServiceBillQueryParam param = new ServiceBillQueryParam();
        param.setProjectName("集成测试");
        Page<ServiceBillDTO> result = serviceBillService.findByParam(param);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("集成测试项目", result.getContent().getFirst().getProjectName());
    }

    @Test
    @DisplayName("测试批量删除服务单据")
    void shouldDeleteServiceBillsSuccessfully() {
        // Given
        ServiceBillDTO createdBill1 = serviceBillService.create(testServiceBillDTO);

        ServiceBillDTO testBill2 = new ServiceBillDTO();
        testBill2.setNumber("SIT-002");
        testBill2.setState(ServiceBillState.CREATED);
        testBill2.setProjectName("测试项目2");
        testBill2.setProjectAddress("测试地址2");
        testBill2.setTotalAmount(new BigDecimal("2000.00"));
        testBill2.setOrderDate(Instant.now());
        testBill2.setDetails(new ArrayList<>());
        testBill2.setAttachments(new ArrayList<>());
        ServiceBillDTO createdBill2 = serviceBillService.create(testBill2);

        List<Integer> idsToDelete = Arrays.asList(createdBill1.getId(), createdBill2.getId());

        // When
        ActionsResult<Integer, Void> result = serviceBillService.delete(idsToDelete);

        // 验证数据库中已删除
        assertThrows(RuntimeException.class, () -> serviceBillService.findById(createdBill1.getId()));
        assertThrows(RuntimeException.class, () -> serviceBillService.findById(createdBill2.getId()));
    }

    @Test
    @DisplayName("测试批量处理服务单据")
    void shouldProcessServiceBillsSuccessfully() {
        // Given
        ServiceBillDTO createdBill = serviceBillService.create(testServiceBillDTO);
        List<Integer> idsToProcess = List.of(createdBill.getId());

        // When
        ActionsResult<Integer, Void> result = serviceBillService.process(idsToProcess);

        // 验证状态已更新
        ServiceBillDTO processedBill = serviceBillService.findById(createdBill.getId());
        assertEquals(ServiceBillState.PROCESSING, processedBill.getState());
    }

    @Test
    @DisplayName("测试标记服务单据为已处理")
    void shouldMarkServiceBillsAsProcessedSuccessfully() {
        // Given
        ServiceBillDTO createdBill = serviceBillService.create(testServiceBillDTO);
        List<Integer> idsToProcess = List.of(createdBill.getId());
        serviceBillService.process(idsToProcess); // 先标记为处理中

        Instant processedDate = Instant.now();

        // When
        ActionsResult<Integer, Void> result = serviceBillService.processed(idsToProcess, processedDate);


        // 验证状态已更新
        ServiceBillDTO processedBill = serviceBillService.findById(createdBill.getId());
        assertEquals(ServiceBillState.PROCESSED, processedBill.getState());
        assertEquals(processedDate, processedBill.getProcessedDate());
    }

    @Test
    @DisplayName("测试完成服务单据")
    void shouldFinishServiceBillsSuccessfully() {
        // Given
        ServiceBillDTO createdBill = serviceBillService.create(testServiceBillDTO);
        List<Integer> idsToProcess = List.of(createdBill.getId());
        serviceBillService.process(idsToProcess); // 先标记为处理中
        Instant processedDate = Instant.now();
        serviceBillService.processed(idsToProcess, processedDate); // 再标记为已处理

        // When
        ActionsResult<Integer, Void> result = serviceBillService.finish(idsToProcess);


        // 验证状态已更新
        ServiceBillDTO finishedBill = serviceBillService.findById(createdBill.getId());
        assertEquals(ServiceBillState.FINISHED, finishedBill.getState());
    }

    @Test
    @DisplayName("测试多状态查询")
    void shouldFindByMultipleStatesSuccessfully() {
        // Given
        ServiceBillDTO createdBill1 = serviceBillService.create(testServiceBillDTO);

        ServiceBillDTO testBill2 = new ServiceBillDTO();
        testBill2.setNumber("SIT-002");
        testBill2.setState(ServiceBillState.PROCESSING);
        testBill2.setProjectName("测试项目2");
        testBill2.setProjectAddress("测试地址2");
        testBill2.setTotalAmount(new BigDecimal("2000.00"));
        testBill2.setOrderDate(Instant.now());
        testBill2.setDetails(new ArrayList<>());
        testBill2.setAttachments(new ArrayList<>());
        ServiceBillDTO createdBill2 = serviceBillService.create(testBill2);

        serviceBillService.process(List.of(createdBill2.getId())); // 将第二个单据改为处理中

        // When - 查询多种状态的单据
        ServiceBillQueryParam param = new ServiceBillQueryParam();
        param.setStates(Arrays.asList(ServiceBillState.CREATED, ServiceBillState.PROCESSING));
        Page<ServiceBillDTO> result = serviceBillService.findByParam(param);

        // Then
        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
        List<ServiceBillState> states = result.getContent().stream()
                .map(ServiceBillDTO::getState)
                .distinct()
                .toList();
        assertTrue(states.contains(ServiceBillState.CREATED));
        assertTrue(states.contains(ServiceBillState.PROCESSING));
    }

}

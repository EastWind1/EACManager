package pers.eastwind.billmanager.security;

import org.junit.jupiter.api.Test;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 @PreAuthorize 中的枚举 getCode() 表达式可被 SpEL 解析
 */
class PermissionSpelTest {
    private final ExpressionParser parser = new SpelExpressionParser();

    @Test
    void enumGetCodeExpressionsResolve() {
        assertEquals("user:query", value(
                "T(pers.eastwind.billmanager.user.permission.UserPermission).QUERY.getCode()"));
        assertEquals("serviceBill:cancel-finish", value(
                "T(pers.eastwind.billmanager.servicebill.permission.ServiceBillPermission).CANCEL_FINISH.getCode()"));
        assertEquals("reimburse:statistic", value(
                "T(pers.eastwind.billmanager.reimburse.permission.ReimbursePermission).STATISTIC.getCode()"));
        assertEquals("permission:update", value(
                "T(pers.eastwind.billmanager.security.model.SecurityPermission).UPDATE.getCode()"));
    }

    @Test
    void hasAuthorityExpressionsResolve() {
        StandardEvaluationContext context = new StandardEvaluationContext(new AuthorityStub());
        Boolean allowed = parser.parseExpression(
                        "hasAuthority(T(pers.eastwind.billmanager.user.permission.UserPermission).QUERY.getCode())")
                .getValue(context, Boolean.class);
        assertTrue(Boolean.TRUE.equals(allowed));
    }

    private Object value(String expression) {
        return parser.parseExpression(expression).getValue();
    }

    public static class AuthorityStub {
        public boolean hasAuthority(String authority) {
            return "user:query".equals(authority);
        }
    }
}

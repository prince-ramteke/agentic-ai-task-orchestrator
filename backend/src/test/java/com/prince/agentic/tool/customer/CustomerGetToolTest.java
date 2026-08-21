package com.prince.agentic.tool.customer;

import com.prince.agentic.customer.CustomerService;
import com.prince.agentic.customer.dto.CustomerResponse;
import com.prince.agentic.security.AuthenticatedUser;
import com.prince.agentic.tool.AbstractToolContractTest;
import com.prince.agentic.tool.Tool;
import com.prince.agentic.tool.ToolExecutionContext;
import com.prince.agentic.tool.ToolRiskLevel;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerGetToolTest extends AbstractToolContractTest {

    private final CustomerService customerService = mock(CustomerService.class);
    private final CustomerGetTool toolUnderTest = new CustomerGetTool(customerService);

    @Override
    protected Tool<?, ?> tool() {
        return toolUnderTest;
    }

    @Test
    void descriptor_is_read_only_customer_get() {
        assertThat(toolUnderTest.descriptor().name()).isEqualTo("customer.get");
        assertThat(toolUnderTest.descriptor().risk()).isEqualTo(ToolRiskLevel.READ_ONLY);
    }

    @Test
    void execute_passes_principal_and_id_to_service() {
        AuthenticatedUser user = new AuthenticatedUser(7L, "u@x.com", Set.of("ROLE_USER"));
        ToolExecutionContext ctx = ToolExecutionContext.forPrincipal(user);
        CustomerResponse expected = mock(CustomerResponse.class);
        when(customerService.get(eq(user), eq(9L))).thenReturn(expected);

        assertThat(toolUnderTest.execute(ctx, new CustomerGetInput(9L))).isSameAs(expected);
        verify(customerService).get(user, 9L);
    }
}

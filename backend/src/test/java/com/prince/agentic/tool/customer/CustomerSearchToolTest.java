package com.prince.agentic.tool.customer;

import com.prince.agentic.common.response.PageResponse;
import com.prince.agentic.customer.CustomerService;
import com.prince.agentic.customer.CustomerStatus;
import com.prince.agentic.customer.dto.CustomerSummaryResponse;
import com.prince.agentic.security.AuthenticatedUser;
import com.prince.agentic.tool.AbstractToolContractTest;
import com.prince.agentic.tool.Tool;
import com.prince.agentic.tool.ToolExecutionContext;
import com.prince.agentic.tool.ToolRiskLevel;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerSearchToolTest extends AbstractToolContractTest {

    private final CustomerService customerService = mock(CustomerService.class);
    private final CustomerSearchTool toolUnderTest = new CustomerSearchTool(customerService);

    @Override
    protected Tool<?, ?> tool() {
        return toolUnderTest;
    }

    @Test
    void descriptor_is_read_only_customer_search() {
        assertThat(toolUnderTest.descriptor().name()).isEqualTo("customer.search");
        assertThat(toolUnderTest.descriptor().risk()).isEqualTo(ToolRiskLevel.READ_ONLY);
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_forwards_filters_and_uses_default_sort() {
        AuthenticatedUser user = new AuthenticatedUser(7L, "u@x.com", Set.of("ROLE_USER"));
        ToolExecutionContext ctx = ToolExecutionContext.forPrincipal(user);
        PageResponse<CustomerSummaryResponse> expected = mock(PageResponse.class);
        when(customerService.list(eq(user), eq(CustomerStatus.ACTIVE), eq("acme"), eq(0), eq(20), isNull()))
                .thenReturn(expected);

        PageResponse<CustomerSummaryResponse> out = toolUnderTest.execute(ctx,
                new CustomerSearchInput(CustomerStatus.ACTIVE, "acme", 0, 20));

        assertThat(out).isSameAs(expected);
        verify(customerService).list(user, CustomerStatus.ACTIVE, "acme", 0, 20, null);
    }
}

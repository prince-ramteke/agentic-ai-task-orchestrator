package com.prince.agentic.tool.customer;

import com.prince.agentic.common.response.PageResponse;
import com.prince.agentic.customer.CustomerService;
import com.prince.agentic.customer.dto.CustomerSummaryResponse;
import com.prince.agentic.security.RoleNames;
import com.prince.agentic.tool.Tool;
import com.prince.agentic.tool.ToolDescriptor;
import com.prince.agentic.tool.ToolExecutionContext;
import com.prince.agentic.tool.ToolRiskLevel;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * {@code customer.search} — list the caller's customers with bounded filters (status + text search).
 * Own-scoped by the service; sort fixed to the service default in M5. READ_ONLY.
 */
@Component
public class CustomerSearchTool implements Tool<CustomerSearchInput, PageResponse<CustomerSummaryResponse>> {

    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "customer.search", "Search the current user's customers by status and text.", "customer", "1",
            ToolRiskLevel.READ_ONLY, true, Set.of(RoleNames.ROLE_USER, RoleNames.ROLE_ADMIN),
            CustomerSearchInput.class, PageResponse.class, Duration.ofSeconds(10));

    private final CustomerService customerService;

    public CustomerSearchTool(CustomerService customerService) {
        this.customerService = customerService;
    }

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public PageResponse<CustomerSummaryResponse> execute(ToolExecutionContext context, CustomerSearchInput input) {
        return customerService.list(context.principal(), input.status(), input.search(),
                input.page(), input.size(), null);   // null sort → service default
    }
}

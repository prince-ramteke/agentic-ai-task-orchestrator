package com.prince.agentic.tool.customer;

import com.prince.agentic.customer.CustomerService;
import com.prince.agentic.customer.dto.CustomerResponse;
import com.prince.agentic.security.RoleNames;
import com.prince.agentic.tool.Tool;
import com.prince.agentic.tool.ToolDescriptor;
import com.prince.agentic.tool.ToolExecutionContext;
import com.prince.agentic.tool.ToolRiskLevel;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * {@code customer.get} — fetch one of the caller's customers by id. Ownership/404-masking/admin-any-by-id
 * enforced by {@link CustomerService}. READ_ONLY.
 */
@Component
public class CustomerGetTool implements Tool<CustomerGetInput, CustomerResponse> {

    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "customer.get", "Get one customer owned by the current user, by id.", "customer", "1",
            ToolRiskLevel.READ_ONLY, true, Set.of(RoleNames.ROLE_USER, RoleNames.ROLE_ADMIN),
            CustomerGetInput.class, CustomerResponse.class, Duration.ofSeconds(10));

    private final CustomerService customerService;

    public CustomerGetTool(CustomerService customerService) {
        this.customerService = customerService;
    }

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public CustomerResponse execute(ToolExecutionContext context, CustomerGetInput input) {
        return customerService.get(context.principal(), input.customerId());
    }
}

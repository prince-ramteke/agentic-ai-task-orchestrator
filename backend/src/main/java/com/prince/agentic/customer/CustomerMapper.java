package com.prince.agentic.customer;

import com.prince.agentic.customer.dto.CustomerResponse;
import com.prince.agentic.customer.dto.CustomerSummaryResponse;

public final class CustomerMapper {

    private CustomerMapper() {
    }

    public static CustomerResponse toResponse(Customer c) {
        return new CustomerResponse(
                c.getId(), c.getOwnerId(), c.getName(), c.getEmail(), c.getPhone(),
                c.getStatus(), c.getCreatedAt(), c.getUpdatedAt());
    }

    public static CustomerSummaryResponse toSummary(Customer c) {
        return new CustomerSummaryResponse(c.getId(), c.getName(), c.getEmail(), c.getStatus());
    }
}

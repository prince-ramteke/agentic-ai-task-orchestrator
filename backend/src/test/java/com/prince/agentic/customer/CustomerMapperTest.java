package com.prince.agentic.customer;

import com.prince.agentic.customer.dto.CustomerResponse;
import com.prince.agentic.customer.dto.CustomerSummaryResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerMapperTest {

    private Customer sample() {
        return new Customer(7L, "Acme", "acme@x.com", "+1 555 0100", CustomerStatus.ACTIVE);
    }

    @Test
    void toResponse_mapsEveryField() {
        CustomerResponse r = CustomerMapper.toResponse(sample());
        assertThat(r.ownerId()).isEqualTo(7L);
        assertThat(r.name()).isEqualTo("Acme");
        assertThat(r.email()).isEqualTo("acme@x.com");
        assertThat(r.phone()).isEqualTo("+1 555 0100");
        assertThat(r.status()).isEqualTo(CustomerStatus.ACTIVE);
    }

    @Test
    void toSummary_mapsListFields() {
        CustomerSummaryResponse s = CustomerMapper.toSummary(sample());
        assertThat(s.name()).isEqualTo("Acme");
        assertThat(s.email()).isEqualTo("acme@x.com");
        assertThat(s.status()).isEqualTo(CustomerStatus.ACTIVE);
    }
}

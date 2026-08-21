package com.prince.agentic.customer;

import com.prince.agentic.support.AbstractPostgresIntegrationTest;
import com.prince.agentic.user.User;
import com.prince.agentic.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies the customers schema on real PostgreSQL: unique(owner,email), status CHECK, FK cascade. */
class CustomerPersistenceIT extends AbstractPostgresIntegrationTest {

    @Autowired private CustomerRepository customerRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void duplicateOwnerEmail_violatesUniqueConstraint() {
        Long ownerId = userRepository.saveAndFlush(new User("cu@example.com", "$2a$h")).getId();
        customerRepository.saveAndFlush(new Customer(ownerId, "A", "dup@x.com", null, CustomerStatus.ACTIVE));
        assertThatThrownBy(() -> customerRepository.saveAndFlush(
                new Customer(ownerId, "B", "dup@x.com", null, CustomerStatus.ACTIVE)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void invalidStatus_isRejectedByCheckConstraint() {
        Long ownerId = userRepository.saveAndFlush(new User("cs@example.com", "$2a$h")).getId();
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO customers (owner_id, name, status) VALUES (?, ?, ?)", ownerId, "x", "PENDING"))
                .isInstanceOf(Exception.class);
    }

    @Test
    void findOwnedFiltered_searchValue_worksOnRealPostgres() {
        Long ownerId = userRepository.saveAndFlush(new User("search@example.com", "$2a$h")).getId();
        customerRepository.saveAndFlush(new Customer(ownerId, "Globex", "info@globex.com", null, CustomerStatus.ACTIVE));
        customerRepository.saveAndFlush(new Customer(ownerId, "Initech", "hi@initech.com", null, CustomerStatus.ACTIVE));
        // The CAST(:search AS string) path must both plan and filter correctly on PostgreSQL.
        assertThat(customerRepository.findOwnedFiltered(ownerId, null, "GLOB", PageRequest.of(0, 10))
                .getContent()).extracting(Customer::getName).containsExactly("Globex");
    }

    @Test
    void deletingOwner_cascadeDeletesCustomers() {
        User owner = userRepository.saveAndFlush(new User("cc@example.com", "$2a$h"));
        customerRepository.saveAndFlush(new Customer(owner.getId(), "A", null, null, CustomerStatus.ACTIVE));
        jdbc.update("DELETE FROM users WHERE id = ?", owner.getId());
        assertThat(customerRepository.findOwnedFiltered(owner.getId(), null, null, PageRequest.of(0, 10))
                .getTotalElements()).isZero();
    }
}

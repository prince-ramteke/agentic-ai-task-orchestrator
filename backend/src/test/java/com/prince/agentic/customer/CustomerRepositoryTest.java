package com.prince.agentic.customer;

import com.prince.agentic.user.User;
import com.prince.agentic.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CustomerRepositoryTest {

    @Autowired private CustomerRepository customerRepository;
    @Autowired private UserRepository userRepository;

    private Long ownerId;

    @BeforeEach
    void setUp() {
        ownerId = userRepository.saveAndFlush(new User("cowner@example.com", "$2a$h")).getId();
    }

    @Test
    void save_and_existsByOwnerIdAndEmail() {
        customerRepository.saveAndFlush(new Customer(ownerId, "Acme", "acme@x.com", null, CustomerStatus.ACTIVE));
        assertThat(customerRepository.existsByOwnerIdAndEmail(ownerId, "acme@x.com")).isTrue();
        assertThat(customerRepository.existsByOwnerIdAndEmail(ownerId, "nope@x.com")).isFalse();
    }

    @Test
    void uniqueOwnerEmail_isEnforced() {
        customerRepository.saveAndFlush(new Customer(ownerId, "A", "dup@x.com", null, CustomerStatus.ACTIVE));
        assertThatThrownBy(() -> customerRepository.saveAndFlush(
                new Customer(ownerId, "B", "dup@x.com", null, CustomerStatus.ACTIVE)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void multipleNullEmails_areAllowedForSameOwner() {
        customerRepository.saveAndFlush(new Customer(ownerId, "A", null, null, CustomerStatus.ACTIVE));
        customerRepository.saveAndFlush(new Customer(ownerId, "B", null, null, CustomerStatus.ACTIVE));
        assertThat(customerRepository.findOwnedFiltered(ownerId, null, null, PageRequest.of(0, 10))
                .getTotalElements()).isEqualTo(2);
    }

    @Test
    void findOwnedFiltered_searchMatchesNameOrEmail_caseInsensitive() {
        customerRepository.saveAndFlush(new Customer(ownerId, "Globex", "info@globex.com", null, CustomerStatus.ACTIVE));
        customerRepository.saveAndFlush(new Customer(ownerId, "Initech", "hi@initech.com", null, CustomerStatus.ACTIVE));

        Page<Customer> byName = customerRepository.findOwnedFiltered(ownerId, null, "glob", PageRequest.of(0, 10));
        assertThat(byName.getContent()).extracting(Customer::getName).containsExactly("Globex");

        Page<Customer> byEmail = customerRepository.findOwnedFiltered(ownerId, null, "INITECH", PageRequest.of(0, 10));
        assertThat(byEmail.getContent()).extracting(Customer::getName).containsExactly("Initech");
    }

    @Test
    void findOwnedFiltered_byStatus() {
        customerRepository.saveAndFlush(new Customer(ownerId, "A", null, null, CustomerStatus.ACTIVE));
        customerRepository.saveAndFlush(new Customer(ownerId, "I", null, null, CustomerStatus.INACTIVE));
        assertThat(customerRepository.findOwnedFiltered(ownerId, CustomerStatus.INACTIVE, null, PageRequest.of(0, 10))
                .getContent()).extracting(Customer::getName).containsExactly("I");
    }
}

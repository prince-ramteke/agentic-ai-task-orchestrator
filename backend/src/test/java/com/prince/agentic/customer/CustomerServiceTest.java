package com.prince.agentic.customer;

import com.prince.agentic.customer.dto.CustomerCreateRequest;
import com.prince.agentic.security.AuthenticatedUser;
import com.prince.agentic.security.AuthorizationService;
import com.prince.agentic.security.RoleNames;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private AuthorizationService authorizationService;
    @InjectMocks private CustomerService customerService;

    private final AuthenticatedUser owner = new AuthenticatedUser(1L, "o@x.com", Set.of(RoleNames.ROLE_USER));
    private final AuthenticatedUser other = new AuthenticatedUser(2L, "b@x.com", Set.of(RoleNames.ROLE_USER));

    private Customer ownedCustomer() {
        return new Customer(1L, "Acme", "acme@x.com", null, CustomerStatus.ACTIVE);
    }

    @Test
    void create_assignsOwner_andDefaultsStatusActive() {
        when(customerRepository.existsByOwnerIdAndEmail(1L, "a@x.com")).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
        var req = new CustomerCreateRequest("Acme", "a@x.com", null, null);

        var res = customerService.create(owner, req);

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        assertThat(captor.getValue().getOwnerId()).isEqualTo(1L);
        assertThat(captor.getValue().getStatus()).isEqualTo(CustomerStatus.ACTIVE);
        assertThat(res.name()).isEqualTo("Acme");
    }

    @Test
    void create_duplicateEmailForOwner_throwsConflict_andDoesNotSave() {
        when(customerRepository.existsByOwnerIdAndEmail(1L, "dup@x.com")).thenReturn(true);
        var req = new CustomerCreateRequest("Acme", "dup@x.com", null, null);
        assertThatThrownBy(() -> customerService.create(owner, req))
                .isInstanceOf(CustomerEmailAlreadyExistsException.class);
        verify(customerRepository, never()).save(any());
    }

    @Test
    void create_nullEmail_skipsDuplicateCheck() {
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
        var req = new CustomerCreateRequest("NoEmail", null, null, null);
        customerService.create(owner, req);
        verify(customerRepository, never()).existsByOwnerIdAndEmail(any(), any());
        verify(customerRepository).save(any());
    }

    @Test
    void get_nonOwner_throwsNotFound() {
        when(customerRepository.findById(5L)).thenReturn(Optional.of(ownedCustomer()));
        when(authorizationService.canAccess(other, 1L)).thenReturn(false);
        assertThatThrownBy(() -> customerService.get(other, 5L))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void delete_owner_deletes() {
        Customer c = ownedCustomer();
        when(customerRepository.findById(5L)).thenReturn(Optional.of(c));
        when(authorizationService.canAccess(owner, 1L)).thenReturn(true);
        customerService.delete(owner, 5L);
        verify(customerRepository).delete(c);
    }
}

package com.prince.agentic.customer;

import com.prince.agentic.common.query.SortWhitelist;
import com.prince.agentic.common.response.PageResponse;
import com.prince.agentic.customer.dto.CustomerCreateRequest;
import com.prince.agentic.customer.dto.CustomerResponse;
import com.prince.agentic.customer.dto.CustomerSummaryResponse;
import com.prince.agentic.customer.dto.CustomerUpdateRequest;
import com.prince.agentic.security.AuthenticatedUser;
import com.prince.agentic.security.AuthorizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Customer business boundary. Mirrors {@link com.prince.agentic.task.TaskService}: server-assigned
 * ownership, authorization via {@link AuthorizationService}, DTO responses. Adds a per-owner unique
 * email rule (pre-check + race-safe DB catch) rendered as 409.
 */
@Service
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private static final SortWhitelist SORT = new SortWhitelist(
            Set.of("createdAt", "updatedAt", "name", "status"), "createdAt", Sort.Direction.DESC);

    private final CustomerRepository customerRepository;
    private final AuthorizationService authorizationService;

    public CustomerService(CustomerRepository customerRepository, AuthorizationService authorizationService) {
        this.customerRepository = customerRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional
    public CustomerResponse create(AuthenticatedUser user, CustomerCreateRequest req) {
        if (req.email() != null && customerRepository.existsByOwnerIdAndEmail(user.userId(), req.email())) {
            throw new CustomerEmailAlreadyExistsException();
        }
        Customer customer = new Customer(
                user.userId(), req.name(), req.email(), req.phone(),
                req.status() == null ? CustomerStatus.ACTIVE : req.status());
        try {
            Customer saved = customerRepository.save(customer);
            log.info("customer.created id={} owner={}", saved.getId(), saved.getOwnerId());
            return CustomerMapper.toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new CustomerEmailAlreadyExistsException();   // lost the race on uq_customers_owner_email
        }
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(AuthenticatedUser user, Long id) {
        return CustomerMapper.toResponse(loadAuthorized(user, id));
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerSummaryResponse> list(AuthenticatedUser user, CustomerStatus status,
                                                      String search, Integer page, Integer size, String sort) {
        Pageable pageable = SORT.toPageable(page, size, sort);
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        Page<Customer> result = customerRepository.findOwnedFiltered(
                user.userId(), status, normalizedSearch, pageable);
        return PageResponse.from(result, CustomerMapper::toSummary);
    }

    @Transactional
    public CustomerResponse update(AuthenticatedUser user, Long id, CustomerUpdateRequest req) {
        Customer customer = loadAuthorized(user, id);
        // If email changes to one already used by another of this owner's customers → 409.
        if (req.email() != null && !req.email().equals(customer.getEmail())
                && customerRepository.existsByOwnerIdAndEmail(user.userId(), req.email())) {
            throw new CustomerEmailAlreadyExistsException();
        }
        customer.setName(req.name());
        customer.setEmail(req.email());
        customer.setPhone(req.phone());
        customer.setStatus(req.status());
        try {
            Customer saved = customerRepository.save(customer);
            log.info("customer.updated id={} owner={}", saved.getId(), saved.getOwnerId());
            return CustomerMapper.toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new CustomerEmailAlreadyExistsException();
        }
    }

    @Transactional
    public void delete(AuthenticatedUser user, Long id) {
        Customer customer = loadAuthorized(user, id);
        customerRepository.delete(customer);
        log.info("customer.deleted id={} owner={}", id, customer.getOwnerId());
    }

    private Customer loadAuthorized(AuthenticatedUser user, Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(id));
        if (!authorizationService.canAccess(user, customer.getOwnerId())) {
            throw new CustomerNotFoundException(id);
        }
        return customer;
    }
}

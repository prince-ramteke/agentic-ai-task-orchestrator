package com.prince.agentic.customer;

import com.prince.agentic.common.response.PageResponse;
import com.prince.agentic.customer.dto.CustomerCreateRequest;
import com.prince.agentic.customer.dto.CustomerResponse;
import com.prince.agentic.customer.dto.CustomerSummaryResponse;
import com.prince.agentic.customer.dto.CustomerUpdateRequest;
import com.prince.agentic.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "Customers", description = "User-owned customer records")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    @Operation(summary = "Create a customer owned by the authenticated user")
    public ResponseEntity<CustomerResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                                   @Valid @RequestBody CustomerCreateRequest request,
                                                   UriComponentsBuilder uriBuilder) {
        CustomerResponse created = customerService.create(user, request);
        URI location = uriBuilder.path("/api/v1/customers/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @Operation(summary = "List the authenticated user's customers (paginated, filterable)")
    public PageResponse<CustomerSummaryResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) CustomerStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return customerService.list(user, status, search, page, size, sort);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one of the user's customers by id")
    public CustomerResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return customerService.get(user, id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Full-replacement update of a customer")
    public CustomerResponse update(@AuthenticationPrincipal AuthenticatedUser user,
                                   @PathVariable Long id,
                                   @Valid @RequestBody CustomerUpdateRequest request) {
        return customerService.update(user, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a customer")
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        customerService.delete(user, id);
    }
}

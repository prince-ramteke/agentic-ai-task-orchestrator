package com.prince.agentic.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence for {@link Customer}. The list query is own-scoped with nullable filters; the
 * search group is parenthesized so it binds correctly against the ownerId/status conditions.
 */
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    boolean existsByOwnerIdAndEmail(Long ownerId, String email);

    // :search is CAST to string so PostgreSQL can type the bind parameter. Without the cast a
    // null String bind is sent as an untyped/bytea value and PG rejects LOWER(bytea) at plan time
    // (H2 tolerates it, real PostgreSQL does not — caught by CustomerPersistenceIT).
    @Query("""
            SELECT c FROM Customer c
            WHERE c.ownerId = :ownerId
              AND (:status IS NULL OR c.status = :status)
              AND (:search IS NULL
                   OR LOWER(c.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                   OR LOWER(c.email) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
            """)
    Page<Customer> findOwnedFiltered(@Param("ownerId") Long ownerId,
                                     @Param("status") CustomerStatus status,
                                     @Param("search") String search,
                                     Pageable pageable);
}

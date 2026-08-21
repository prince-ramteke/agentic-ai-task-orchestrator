package com.prince.agentic.task;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

/**
 * Persistence for {@link Task}. Ownership filtering happens in SQL — never load-all-then-filter.
 * The list query takes nullable optional filters so one method serves every filter combination
 * without a combinatorial explosion of derived methods.
 */
public interface TaskRepository extends JpaRepository<Task, Long> {

    @Query("""
            SELECT t FROM Task t
            WHERE t.ownerId = :ownerId
              AND (:status IS NULL OR t.status = :status)
              AND (:priority IS NULL OR t.priority = :priority)
              AND (:dueBefore IS NULL OR t.dueDate <= :dueBefore)
            """)
    Page<Task> findOwnedFiltered(@Param("ownerId") Long ownerId,
                                 @Param("status") TaskStatus status,
                                 @Param("priority") TaskPriority priority,
                                 @Param("dueBefore") LocalDate dueBefore,
                                 Pageable pageable);
}

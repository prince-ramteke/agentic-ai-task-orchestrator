package com.prince.agentic.task;

import com.prince.agentic.support.AbstractPostgresIntegrationTest;
import com.prince.agentic.user.User;
import com.prince.agentic.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies the tasks schema behaves correctly on real PostgreSQL: CHECK constraints and FK cascade. */
class TaskPersistenceIT extends AbstractPostgresIntegrationTest {

    @Autowired private TaskRepository taskRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void invalidStatus_isRejectedByCheckConstraint() {
        Long ownerId = userRepository.saveAndFlush(new User("chk@example.com", "$2a$h")).getId();
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO tasks (owner_id, title, status, priority) VALUES (?, ?, ?, ?)",
                ownerId, "bad", "BOGUS", "LOW"))
                .isInstanceOf(Exception.class);
    }

    @Test
    void negativeEstimatedHours_isRejectedByCheckConstraint() {
        Long ownerId = userRepository.saveAndFlush(new User("neg@example.com", "$2a$h")).getId();
        Task t = new Task(ownerId, "x", null, TaskStatus.TODO, TaskPriority.LOW,
                new BigDecimal("-1.00"), LocalDate.now());
        assertThatThrownBy(() -> taskRepository.saveAndFlush(t))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingOwner_cascadeDeletesTasks() {
        User owner = userRepository.saveAndFlush(new User("cascade@example.com", "$2a$h"));
        taskRepository.saveAndFlush(new Task(owner.getId(), "t", null, TaskStatus.TODO,
                TaskPriority.LOW, null, null));
        jdbc.update("DELETE FROM users WHERE id = ?", owner.getId());
        assertThat(taskRepository.findOwnedFiltered(owner.getId(), null, null, null,
                PageRequest.of(0, 10)).getTotalElements()).isZero();
    }
}

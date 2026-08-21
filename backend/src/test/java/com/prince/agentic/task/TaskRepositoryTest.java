package com.prince.agentic.task;

import com.prince.agentic.user.User;
import com.prince.agentic.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TaskRepositoryTest {

    @Autowired private TaskRepository taskRepository;
    @Autowired private UserRepository userRepository;

    private Long ownerId;
    private Long otherOwnerId;

    @BeforeEach
    void setUp() {
        ownerId = userRepository.saveAndFlush(new User("owner@example.com", "$2a$hash")).getId();
        otherOwnerId = userRepository.saveAndFlush(new User("other@example.com", "$2a$hash")).getId();
    }

    private Task task(Long owner, String title, TaskStatus status, TaskPriority priority, LocalDate due) {
        return taskRepository.saveAndFlush(
                new Task(owner, title, null, status, priority, new BigDecimal("1.50"), due));
    }

    @Test
    void save_persistsWithGeneratedIdAndTimestamps() {
        Task saved = task(ownerId, "Write plan", TaskStatus.TODO, TaskPriority.HIGH, LocalDate.now());
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void findOwnedFiltered_noFilters_returnsOnlyOwnersTasks() {
        task(ownerId, "Mine", TaskStatus.TODO, TaskPriority.LOW, null);
        task(otherOwnerId, "Theirs", TaskStatus.TODO, TaskPriority.LOW, null);

        Page<Task> page = taskRepository.findOwnedFiltered(
                ownerId, null, null, null, PageRequest.of(0, 20, Sort.by("id")));

        assertThat(page.getContent()).extracting(Task::getTitle).containsExactly("Mine");
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void findOwnedFiltered_byStatusAndPriority_appliesBoth() {
        task(ownerId, "A", TaskStatus.IN_PROGRESS, TaskPriority.HIGH, null);
        task(ownerId, "B", TaskStatus.IN_PROGRESS, TaskPriority.LOW, null);
        task(ownerId, "C", TaskStatus.TODO, TaskPriority.HIGH, null);

        Page<Task> page = taskRepository.findOwnedFiltered(
                ownerId, TaskStatus.IN_PROGRESS, TaskPriority.HIGH, null, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Task::getTitle).containsExactly("A");
    }

    @Test
    void findOwnedFiltered_dueBefore_returnsOnlyOnOrBeforeDate() {
        task(ownerId, "Overdue", TaskStatus.TODO, TaskPriority.LOW, LocalDate.parse("2020-01-01"));
        task(ownerId, "Future", TaskStatus.TODO, TaskPriority.LOW, LocalDate.parse("2999-01-01"));

        Page<Task> page = taskRepository.findOwnedFiltered(
                ownerId, null, null, LocalDate.parse("2021-01-01"), PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Task::getTitle).containsExactly("Overdue");
    }

    @Test
    void findOwnedFiltered_paginates() {
        for (int i = 0; i < 25; i++) {
            task(ownerId, "T" + i, TaskStatus.TODO, TaskPriority.LOW, null);
        }
        Page<Task> page = taskRepository.findOwnedFiltered(ownerId, null, null, null, PageRequest.of(0, 20));
        assertThat(page.getContent()).hasSize(20);
        assertThat(page.getTotalElements()).isEqualTo(25);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }
}

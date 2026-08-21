package com.prince.agentic.task;

import com.prince.agentic.task.dto.TaskResponse;
import com.prince.agentic.task.dto.TaskSummaryResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TaskMapperTest {

    private Task sampleTask() {
        return new Task(42L, "Ship M3", "do it", TaskStatus.IN_PROGRESS, TaskPriority.HIGH,
                new BigDecimal("3.25"), LocalDate.parse("2026-09-01"));
    }

    @Test
    void toResponse_mapsEveryField() {
        TaskResponse r = TaskMapper.toResponse(sampleTask());
        assertThat(r.ownerId()).isEqualTo(42L);
        assertThat(r.title()).isEqualTo("Ship M3");
        assertThat(r.description()).isEqualTo("do it");
        assertThat(r.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(r.priority()).isEqualTo(TaskPriority.HIGH);
        assertThat(r.estimatedHours()).isEqualByComparingTo("3.25");
        assertThat(r.dueDate()).isEqualTo(LocalDate.parse("2026-09-01"));
    }

    @Test
    void toSummary_mapsOnlyListFields() {
        TaskSummaryResponse s = TaskMapper.toSummary(sampleTask());
        assertThat(s.title()).isEqualTo("Ship M3");
        assertThat(s.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(s.priority()).isEqualTo(TaskPriority.HIGH);
        assertThat(s.dueDate()).isEqualTo(LocalDate.parse("2026-09-01"));
    }
}

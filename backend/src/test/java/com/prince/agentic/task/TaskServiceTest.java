package com.prince.agentic.task;

import com.prince.agentic.security.AuthenticatedUser;
import com.prince.agentic.security.AuthorizationService;
import com.prince.agentic.security.RoleNames;
import com.prince.agentic.task.dto.TaskCreateRequest;
import com.prince.agentic.task.dto.TaskResponse;
import com.prince.agentic.task.dto.TaskUpdateRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private AuthorizationService authorizationService;
    @InjectMocks private TaskService taskService;

    private final AuthenticatedUser owner = new AuthenticatedUser(1L, "o@x.com", Set.of(RoleNames.ROLE_USER));
    private final AuthenticatedUser other = new AuthenticatedUser(2L, "b@x.com", Set.of(RoleNames.ROLE_USER));
    private final AuthenticatedUser admin = new AuthenticatedUser(9L, "a@x.com", Set.of(RoleNames.ROLE_ADMIN));

    private Task ownedTask() {
        return new Task(1L, "t", null, TaskStatus.TODO, TaskPriority.MEDIUM, null, null);
    }

    @Test
    void create_assignsOwnerFromPrincipal_andDefaultsStatusAndPriority() {
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
        var req = new TaskCreateRequest("New", null, null, null, new BigDecimal("2.00"), null);

        TaskResponse res = taskService.create(owner, req);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        assertThat(captor.getValue().getOwnerId()).isEqualTo(1L);      // from principal, not client
        assertThat(captor.getValue().getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(captor.getValue().getPriority()).isEqualTo(TaskPriority.MEDIUM);
        assertThat(res.status()).isEqualTo(TaskStatus.TODO);
    }

    @Test
    void get_ownerCanAccess_returnsTask() {
        when(taskRepository.findById(5L)).thenReturn(Optional.of(ownedTask()));
        when(authorizationService.canAccess(owner, 1L)).thenReturn(true);
        assertThat(taskService.get(owner, 5L).title()).isEqualTo("t");
    }

    @Test
    void get_nonOwner_throwsNotFound_notForbidden() {
        when(taskRepository.findById(5L)).thenReturn(Optional.of(ownedTask()));
        when(authorizationService.canAccess(other, 1L)).thenReturn(false);
        assertThatThrownBy(() -> taskService.get(other, 5L)).isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void get_missing_throwsNotFound() {
        when(taskRepository.findById(5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> taskService.get(owner, 5L)).isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void get_admin_canAccessOthersTask() {
        when(taskRepository.findById(5L)).thenReturn(Optional.of(ownedTask()));
        when(authorizationService.canAccess(admin, 1L)).thenReturn(true);   // admin bypass in canAccess
        assertThat(taskService.get(admin, 5L)).isNotNull();
    }

    @Test
    void update_nonOwner_throwsNotFound_andDoesNotSave() {
        when(taskRepository.findById(5L)).thenReturn(Optional.of(ownedTask()));
        when(authorizationService.canAccess(other, 1L)).thenReturn(false);
        var req = new TaskUpdateRequest("x", null, TaskStatus.COMPLETED, TaskPriority.LOW, null, null);
        assertThatThrownBy(() -> taskService.update(other, 5L, req)).isInstanceOf(TaskNotFoundException.class);
        verify(taskRepository, never()).save(any());
    }

    @Test
    void delete_owner_deletes() {
        Task t = ownedTask();
        when(taskRepository.findById(5L)).thenReturn(Optional.of(t));
        when(authorizationService.canAccess(owner, 1L)).thenReturn(true);
        taskService.delete(owner, 5L);
        verify(taskRepository).delete(t);
    }
}

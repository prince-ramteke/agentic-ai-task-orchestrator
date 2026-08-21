package com.prince.agentic.task;

import com.prince.agentic.common.response.PageResponse;
import com.prince.agentic.security.AuthenticatedUser;
import com.prince.agentic.task.dto.TaskCreateRequest;
import com.prince.agentic.task.dto.TaskResponse;
import com.prince.agentic.task.dto.TaskSummaryResponse;
import com.prince.agentic.task.dto.TaskUpdateRequest;
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
import java.time.LocalDate;

/**
 * Task CRUD. Thin: it resolves the authenticated principal and delegates to {@link TaskService},
 * which owns authorization and business rules. All routes require authentication (deny-by-default).
 */
@RestController
@RequestMapping("/api/v1/tasks")
@Tag(name = "Tasks", description = "User-owned tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    @Operation(summary = "Create a task owned by the authenticated user")
    public ResponseEntity<TaskResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                               @Valid @RequestBody TaskCreateRequest request,
                                               UriComponentsBuilder uriBuilder) {
        TaskResponse created = taskService.create(user, request);
        URI location = uriBuilder.path("/api/v1/tasks/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @Operation(summary = "List the authenticated user's tasks (paginated, filterable)")
    public PageResponse<TaskSummaryResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) LocalDate dueBefore,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return taskService.list(user, status, priority, dueBefore, page, size, sort);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one of the user's tasks by id")
    public TaskResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return taskService.get(user, id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Full-replacement update of a task")
    public TaskResponse update(@AuthenticationPrincipal AuthenticatedUser user,
                               @PathVariable Long id,
                               @Valid @RequestBody TaskUpdateRequest request) {
        return taskService.update(user, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a task")
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        taskService.delete(user, id);
    }
}

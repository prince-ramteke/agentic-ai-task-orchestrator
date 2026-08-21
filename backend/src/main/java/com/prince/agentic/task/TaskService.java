package com.prince.agentic.task;

import com.prince.agentic.common.query.SortWhitelist;
import com.prince.agentic.common.response.PageResponse;
import com.prince.agentic.security.AuthenticatedUser;
import com.prince.agentic.security.AuthorizationService;
import com.prince.agentic.task.dto.TaskCreateRequest;
import com.prince.agentic.task.dto.TaskResponse;
import com.prince.agentic.task.dto.TaskSummaryResponse;
import com.prince.agentic.task.dto.TaskUpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Set;

/**
 * Task business boundary. This is the API future AI tools call: every mutation authorizes the
 * authenticated principal (never a client/model claim) via {@link AuthorizationService}, assigns
 * ownership server-side, and returns DTOs. Controllers stay thin; no transaction spans a slow call.
 */
@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private static final SortWhitelist SORT = new SortWhitelist(
            Set.of("createdAt", "updatedAt", "dueDate", "priority", "status", "title"),
            "createdAt", Sort.Direction.DESC);

    private final TaskRepository taskRepository;
    private final AuthorizationService authorizationService;

    public TaskService(TaskRepository taskRepository, AuthorizationService authorizationService) {
        this.taskRepository = taskRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional
    public TaskResponse create(AuthenticatedUser user, TaskCreateRequest req) {
        Task task = new Task(
                user.userId(),                                   // owner from the principal — never the client
                req.title(),
                req.description(),
                req.status() == null ? TaskStatus.TODO : req.status(),
                req.priority() == null ? TaskPriority.MEDIUM : req.priority(),
                req.estimatedHours(),
                req.dueDate());
        Task saved = taskRepository.save(task);
        log.info("task.created id={} owner={}", saved.getId(), saved.getOwnerId());
        return TaskMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public TaskResponse get(AuthenticatedUser user, Long id) {
        return TaskMapper.toResponse(loadAuthorized(user, id));
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskSummaryResponse> list(AuthenticatedUser user, TaskStatus status,
                                                  TaskPriority priority, LocalDate dueBefore,
                                                  Integer page, Integer size, String sort) {
        Pageable pageable = SORT.toPageable(page, size, sort);
        Page<Task> result = taskRepository.findOwnedFiltered(
                user.userId(), status, priority, dueBefore, pageable);   // own-scoped for USER and ADMIN
        return PageResponse.from(result, TaskMapper::toSummary);
    }

    @Transactional
    public TaskResponse update(AuthenticatedUser user, Long id, TaskUpdateRequest req) {
        Task task = loadAuthorized(user, id);
        task.setTitle(req.title());
        task.setDescription(req.description());
        task.setStatus(req.status());
        task.setPriority(req.priority());
        task.setEstimatedHours(req.estimatedHours());
        task.setDueDate(req.dueDate());
        Task saved = taskRepository.save(task);
        log.info("task.updated id={} owner={}", saved.getId(), saved.getOwnerId());
        return TaskMapper.toResponse(saved);
    }

    @Transactional
    public void delete(AuthenticatedUser user, Long id) {
        Task task = loadAuthorized(user, id);
        taskRepository.delete(task);
        log.info("task.deleted id={} owner={}", id, task.getOwnerId());
    }

    /**
     * Load a task the caller may act on. Missing → 404. Loaded but not accessible (non-owner USER)
     * → 404 as well (existence-masking). Admin bypass is encoded in {@code canAccess}.
     */
    private Task loadAuthorized(AuthenticatedUser user, Long id) {
        Task task = taskRepository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
        if (!authorizationService.canAccess(user, task.getOwnerId())) {
            throw new TaskNotFoundException(id);
        }
        return task;
    }
}

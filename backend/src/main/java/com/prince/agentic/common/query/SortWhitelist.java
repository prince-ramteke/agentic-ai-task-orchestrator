package com.prince.agentic.common.query;

import com.prince.agentic.common.exception.InvalidRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Turns client pagination/sort params into a safe {@link Pageable}: page ≥ 0, size clamped to
 * [1, 100] (default 20), and sort restricted to an explicit whitelist of entity properties so a
 * client can never sort by an arbitrary/sensitive column. Unknown field → {@link InvalidRequestException} (400).
 */
public class SortWhitelist {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final Set<String> allowedFields;
    private final String defaultField;
    private final Sort.Direction defaultDirection;

    public SortWhitelist(Set<String> allowedFields, String defaultField, Sort.Direction defaultDirection) {
        this.allowedFields = allowedFields;
        this.defaultField = defaultField;
        this.defaultDirection = defaultDirection;
    }

    public Pageable toPageable(Integer page, Integer size, String sort) {
        int p = page == null || page < 0 ? 0 : page;
        int s = size == null ? DEFAULT_SIZE : Math.min(Math.max(size, 1), MAX_SIZE);
        return PageRequest.of(p, s, parseSort(sort));
    }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(defaultDirection, defaultField);
        }
        String[] parts = sort.split(",", 2);
        String field = parts[0].trim();
        if (!allowedFields.contains(field)) {
            throw new InvalidRequestException("Unsupported sort field: '" + field + "'");
        }
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, field);
    }
}

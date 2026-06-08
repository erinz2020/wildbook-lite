package com.wildme.wildbook_lite.common;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * Stable, framework-agnostic pagination envelope.
 *
 * Why not just return Spring's Page<T>:
 *  - Page<T> serializes with Spring-internal field names (e.g., "pageable"
 *    object, "sort.empty", "sort.unsorted") that leak Spring internals into
 *    the API contract and changed wire format between Spring Boot versions.
 *  - A flat record is also easier to consume in a frontend.
 */
public record PageResponse<T>(
    List<T> items,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public static <S, T> PageResponse<T> from(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(
            page.getContent().stream().map(mapper).toList(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }

    public static <T> PageResponse<T> from(Page<T> page) {
        return from(page, Function.identity());
    }
}

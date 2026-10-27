package com.intics.modelradar.web;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Compact pagination envelope for list endpoints: just the fields the dashboard
 * needs ({@code content}, {@code totalElements}, {@code page}, {@code size}) rather
 * than Spring Data's verbose {@code Page} serialization.
 */
public record PagedResponse<T>(List<T> content, long totalElements, int page, int size) {

    /** Build from a Spring Data {@link Page}, mapping each element to the DTO type. */
    public static <E, T> PagedResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PagedResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getTotalElements(),
                page.getNumber(),
                page.getSize());
    }
}

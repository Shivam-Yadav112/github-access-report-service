package com.example.githubaccessreport.model.report;

import java.util.List;

/**
 * Wraps a slice of a larger collection so clients dealing with 1000+ users
 * don't have to pull the entire report body in one response.
 */
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T> PagedResponse<T> of(List<T> all, int page, int size) {
        int total = all.size();
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / size);
        int fromIndex = Math.min(page * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        return new PagedResponse<>(all.subList(fromIndex, toIndex), page, size, total, totalPages);
    }
}

package com.platform.common.web.paging;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Stable JSON envelope for paginated collections.
 *
 * <p>Spring's {@code PageImpl} is intentionally not serialised directly: its JSON shape is an
 * implementation detail that has changed between releases, and APIs must not break when it does.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        List<String> sort) {

    public PageResponse {
        content = List.copyOf(content);
        sort = List.copyOf(sort);
    }

    public static <E, T> PageResponse<T> from(Page<E> page, Function<? super E, ? extends T> mapper) {
        List<T> content = page.getContent().stream().<T>map(mapper).toList();
        List<String> sort = page.getSort().stream()
                .map(order -> order.getProperty() + "," + order.getDirection().name().toLowerCase())
                .toList();
        return new PageResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast(), sort);
    }
}

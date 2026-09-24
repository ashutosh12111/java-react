package com.platform.common.web.paging;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Pages and sorts an in-memory stream the same way a database would.
 *
 * <p>Phase 1 only: services keep data in memory until Phase 3 introduces PostgreSQL, at which point
 * Spring Data performs paging/sorting in SQL and this class is deleted.
 */
public final class InMemoryPageSupport {

    private InMemoryPageSupport() {
    }

    public static <T> Page<T> page(
            Stream<T> items, Pageable pageable, Map<String, Comparator<T>> sortable, Comparator<T> defaultOrder) {
        SortValidator.validate(pageable.getSort(), sortable.keySet());

        Comparator<T> comparator = pageable.getSort().stream()
                .map(order -> directed(sortable.get(order.getProperty()), order.getDirection()))
                .reduce(Comparator::thenComparing)
                .map(c -> c.thenComparing(defaultOrder))
                .orElse(defaultOrder);

        List<T> sorted = items.sorted(comparator).toList();
        if (pageable.isUnpaged()) {
            return new PageImpl<>(sorted, pageable, sorted.size());
        }
        int from = (int) Math.min(pageable.getOffset(), sorted.size());
        int to = Math.min(from + pageable.getPageSize(), sorted.size());
        return new PageImpl<>(sorted.subList(from, to), pageable, sorted.size());
    }

    private static <T> Comparator<T> directed(Comparator<T> comparator, Sort.Direction direction) {
        return direction.isDescending() ? comparator.reversed() : comparator;
    }
}

package com.platform.common.web.paging;

import com.platform.common.web.error.BadRequestException;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.data.domain.Sort;

/**
 * Rejects sort properties that are not explicitly allowed.
 *
 * <p>Whitelisting keeps the public API decoupled from internal field names and prevents clients from
 * sorting on unindexed (slow) or sensitive columns once persistence is backed by a database.
 */
public final class SortValidator {

    private SortValidator() {
    }

    public static void validate(Sort sort, Set<String> allowed) {
        for (Sort.Order order : sort) {
            if (!allowed.contains(order.getProperty())) {
                throw new BadRequestException("INVALID_SORT_PROPERTY",
                        "Cannot sort by '" + order.getProperty() + "'. Allowed: " + new TreeSet<>(allowed));
            }
        }
    }
}

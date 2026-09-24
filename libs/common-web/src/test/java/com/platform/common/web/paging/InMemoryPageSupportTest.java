package com.platform.common.web.paging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.platform.common.web.error.BadRequestException;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class InMemoryPageSupportTest {

    record Item(String name, int rank) {
    }

    private static final Map<String, Comparator<Item>> SORTABLE = Map.of(
            "name", Comparator.comparing(Item::name),
            "rank", Comparator.comparingInt(Item::rank));

    private static Stream<Item> items() {
        return Stream.of(new Item("c", 1), new Item("a", 3), new Item("b", 2), new Item("d", 2));
    }

    @Test
    void sortsAndSlices() {
        Page<Item> page = InMemoryPageSupport.page(items(),
                PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "rank")), SORTABLE, Comparator.comparing(Item::name));

        assertThat(page.getContent()).extracting(Item::name).containsExactly("a", "b");
        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    void pageBeyondTheEndIsEmpty() {
        Page<Item> page = InMemoryPageSupport.page(items(), PageRequest.of(5, 10), SORTABLE, Comparator.comparing(Item::name));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(4);
    }

    @Test
    void rejectsUnknownSortProperty() {
        assertThatThrownBy(() -> InMemoryPageSupport.page(items(),
                PageRequest.of(0, 2, Sort.by("password")), SORTABLE, Comparator.comparing(Item::name)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("password");
    }

    @Test
    void pageResponseExposesStableShape() {
        Page<Item> page = InMemoryPageSupport.page(items(),
                PageRequest.of(1, 3, Sort.by("name")), SORTABLE, Comparator.comparing(Item::name));

        PageResponse<String> response = PageResponse.from(page, Item::name);

        assertThat(response.content()).containsExactly("d");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.last()).isTrue();
        assertThat(response.sort()).containsExactly("name,asc");
    }
}

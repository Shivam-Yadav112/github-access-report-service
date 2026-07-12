package com.example.githubaccessreport.model.report;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class PagedResponseTest {

    private final List<Integer> thousandUsers = IntStream.rangeClosed(1, 1000).boxed().toList();

    @Test
    void slicesFirstPageCorrectly() {
        PagedResponse<Integer> page = PagedResponse.of(thousandUsers, 0, 50);

        assertThat(page.content()).hasSize(50).containsExactly(
                IntStream.rangeClosed(1, 50).boxed().toArray(Integer[]::new));
        assertThat(page.totalElements()).isEqualTo(1000);
        assertThat(page.totalPages()).isEqualTo(20);
    }

    @Test
    void slicesLastPartialPageCorrectly() {
        // 1000 items, size 300 -> pages of 300, 300, 300, 100
        PagedResponse<Integer> page = PagedResponse.of(thousandUsers, 3, 300);

        assertThat(page.content()).hasSize(100);
        assertThat(page.totalPages()).isEqualTo(4);
    }

    @Test
    void pageBeyondRangeReturnsEmptyContent() {
        PagedResponse<Integer> page = PagedResponse.of(thousandUsers, 99, 50);

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isEqualTo(1000);
    }
}

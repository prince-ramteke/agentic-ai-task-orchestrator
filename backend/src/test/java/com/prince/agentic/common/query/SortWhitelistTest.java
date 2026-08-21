package com.prince.agentic.common.query;

import com.prince.agentic.common.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SortWhitelistTest {

    private final SortWhitelist whitelist =
            new SortWhitelist(Set.of("createdAt", "title", "dueDate"), "createdAt", Sort.Direction.DESC);

    @Test
    void toPageable_nullParams_appliesDefaults() {
        Pageable p = whitelist.toPageable(null, null, null);
        assertThat(p.getPageNumber()).isZero();
        assertThat(p.getPageSize()).isEqualTo(20);
        assertThat(p.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void toPageable_sizeAboveMax_clampsTo100() {
        assertThat(whitelist.toPageable(0, 1_000_000, null).getPageSize()).isEqualTo(100);
    }

    @Test
    void toPageable_sizeZeroOrNegative_clampsToOne() {
        assertThat(whitelist.toPageable(0, 0, null).getPageSize()).isEqualTo(1);
        assertThat(whitelist.toPageable(0, -5, null).getPageSize()).isEqualTo(1);
    }

    @Test
    void toPageable_negativePage_clampsToZero() {
        assertThat(whitelist.toPageable(-3, 20, null).getPageNumber()).isZero();
    }

    @Test
    void toPageable_validSortAsc_parsesFieldAndDirection() {
        Sort.Order order = whitelist.toPageable(0, 20, "title,asc").getSort().getOrderFor("title");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void toPageable_unknownSortField_throwsInvalidRequest() {
        assertThatThrownBy(() -> whitelist.toPageable(0, 20, "password,asc"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void toPageable_sortWithoutDirection_defaultsToAsc() {
        assertThat(whitelist.toPageable(0, 20, "title").getSort().getOrderFor("title").getDirection())
                .isEqualTo(Sort.Direction.ASC);
    }
}

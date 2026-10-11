package com.tradeevo.core.rule;

import static com.tradeevo.core.rule.Truth.FALSE;
import static com.tradeevo.core.rule.Truth.TRUE;
import static com.tradeevo.core.rule.Truth.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TruthTest {

    @Test
    void kleeneNot() {
        assertThat(TRUE.not()).isEqualTo(FALSE);
        assertThat(FALSE.not()).isEqualTo(TRUE);
        assertThat(UNKNOWN.not()).isEqualTo(UNKNOWN);
    }

    @Test
    void kleeneAnd() {
        assertThat(TRUE.and(TRUE)).isEqualTo(TRUE);
        assertThat(TRUE.and(FALSE)).isEqualTo(FALSE);
        assertThat(TRUE.and(UNKNOWN)).isEqualTo(UNKNOWN);
        assertThat(FALSE.and(UNKNOWN)).isEqualTo(FALSE);
        assertThat(UNKNOWN.and(FALSE)).isEqualTo(FALSE);
        assertThat(UNKNOWN.and(UNKNOWN)).isEqualTo(UNKNOWN);
    }

    @Test
    void kleeneOr() {
        assertThat(FALSE.or(FALSE)).isEqualTo(FALSE);
        assertThat(FALSE.or(TRUE)).isEqualTo(TRUE);
        assertThat(FALSE.or(UNKNOWN)).isEqualTo(UNKNOWN);
        assertThat(TRUE.or(UNKNOWN)).isEqualTo(TRUE);
        assertThat(UNKNOWN.or(TRUE)).isEqualTo(TRUE);
        assertThat(UNKNOWN.or(UNKNOWN)).isEqualTo(UNKNOWN);
    }
}

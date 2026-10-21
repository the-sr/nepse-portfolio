package com.nepse.portfolio.service.impl;

import com.nepse.portfolio.service.impl.NepseTokenCleaner;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NepseTokenCleanerTest {

    @Test
    void removesCharactersAtGivenPositions() {
        assertThat(NepseTokenCleaner.clean("ABCDEFGHIJ", new int[]{1, 3, 5}))
                .isEqualTo("ACEGHIJ");
    }

    @Test
    void toleratesUnsortedIndexes() {
        assertThat(NepseTokenCleaner.clean("ABCDEFGHIJ", new int[]{5, 1, 3}))
                .isEqualTo("ACEGHIJ");
    }

    @Test
    void rejectsIndexOutsideToken() {
        assertThatThrownBy(() -> NepseTokenCleaner.clean("ABC", new int[]{3}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDuplicateIndexes() {
        assertThatThrownBy(() -> NepseTokenCleaner.clean("ABCDE", new int[]{2, 2}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEmptyToken() {
        assertThatThrownBy(() -> NepseTokenCleaner.clean("", new int[]{0}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

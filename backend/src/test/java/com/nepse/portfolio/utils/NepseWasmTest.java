package com.nepse.portfolio.utils;

import com.nepse.portfolio.utils.NepseWasm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NepseWasmTest {

    @Test
    void computesKnownTokenIndexes() {
        NepseWasm wasm = new NepseWasm();

        int[] indexes = wasm.tokenIndexes(new long[]{54407, 32169, 69450, 45597, 97190});

        assertThat(indexes).containsExactly(31, 48, 76, 103, 120);
    }

    @Test
    void rejectsWrongNumberOfSalts() {
        NepseWasm wasm = new NepseWasm();

        assertThatThrownBy(() -> wasm.tokenIndexes(new long[]{1, 2, 3}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

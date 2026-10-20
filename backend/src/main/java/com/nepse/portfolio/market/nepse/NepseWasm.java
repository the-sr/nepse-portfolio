package com.nepse.portfolio.market.nepse;

import com.dylibso.chicory.runtime.ExportFunction;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.WasmModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * Wraps NEPSE's own 750-byte {@code css.wasm} module, the same public asset the
 * nepalstock.com frontend loads from
 * {@code https://www.nepalstock.com/assets/prod/css.wasm}.
 *
 * <p>Its exported functions ({@code cdx, rdx, bdx, ndx, mdx}) take the five salt
 * values from the authentication response and return the positions of the junk
 * characters inside the token. Running the vendor-supplied module keeps us in
 * sync with NEPSE instead of re-implementing a moving target.</p>
 */
@Component
public class NepseWasm {

    private static final Logger log = LoggerFactory.getLogger(NepseWasm.class);
    private static final String RESOURCE = "nepse/css.wasm";

    private final Instance instance;

    public NepseWasm() {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            WasmModule module = Parser.parse(in.readAllBytes());
            this.instance = Instance.builder(module).build();
            log.debug("Loaded NEPSE token module from classpath:{}", RESOURCE);
        } catch (IOException | RuntimeException ex) {
            throw new IllegalStateException("Could not load NEPSE token module " + RESOURCE, ex);
        }
    }

    /**
     * @param salts salt1..salt5 from {@code /api/authenticate/prove}
     * @return five token indexes, in ascending order
     */
    public synchronized int[] tokenIndexes(long[] salts) {
        if (salts == null || salts.length != 5) {
            throw new IllegalArgumentException("Expected exactly five salts");
        }
        // Argument order mirrors the nepalstock.com frontend and the reference client.
        int[] indexes = {
                invoke("cdx", salts[0], salts[1], salts[2], salts[3], salts[4]),
                invoke("rdx", salts[0], salts[1], salts[3], salts[2], salts[4]),
                invoke("bdx", salts[0], salts[1], salts[3], salts[2], salts[4]),
                invoke("ndx", salts[0], salts[1], salts[3], salts[2], salts[4]),
                invoke("mdx", salts[0], salts[1], salts[3], salts[2], salts[4])
        };
        for (int i = 1; i < indexes.length; i++) {
            if (indexes[i] <= indexes[i - 1]) {
                throw new IllegalStateException(
                        "NEPSE token module returned non-ascending indexes: "
                                + java.util.Arrays.toString(indexes));
            }
        }
        return indexes;
    }

    private int invoke(String function, long... args) {
        ExportFunction export = instance.export(function);
        long[] result = export.apply(args);
        if (result == null || result.length == 0) {
            throw new IllegalStateException("NEPSE token module export '" + function + "' returned nothing");
        }
        return (int) result[0];
    }
}

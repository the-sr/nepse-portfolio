package com.nepse.portfolio.service.market;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nepse.portfolio.service.market.SecurityUniverseService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Imports NEPSE's live security universe on startup so a fresh install is
 * populated with real securities instead of a static seed.
 *
 * <p>Failures are non-fatal: if NEPSE is unreachable the app still boots with
 * whatever is already in the {@code stocks} table, and the import can be run
 * again at any time via {@code POST /api/admin/market/universe}.</p>
 */
@Component
@Order(1)
@Slf4j
@RequiredArgsConstructor
public class UniverseStartupRunner implements CommandLineRunner {


    private final SecurityUniverseService universeService;

    @Value("${app.nepse.enabled:true}")
    private boolean nepseEnabled;
    @Value("${app.nepse.universe-on-startup:true}")
    private boolean enabled;

    @Override
    public void run(String... args) {
        if (!nepseEnabled || !enabled) {
            log.info("NEPSE universe import on startup is disabled "
                    + "(app.nepse.enabled={}, app.nepse.universe-on-startup={})", nepseEnabled, enabled);
            return;
        }
        try {
            SecurityUniverseService.Result result = universeService.sync();
            log.info("NEPSE universe import on startup: {} securities, {} added, {} updated, {} deactivated",
                    result.activeSecurities(), result.added(), result.updated(), result.deactivated());
        } catch (Exception ex) {
            log.warn("NEPSE universe import on startup failed; continuing with the existing stock table: {}",
                    ex.getMessage());
        }
    }
}

package com.nepse.portfolio.service.impl;

import com.nepse.portfolio.domain.Role;
import com.nepse.portfolio.domain.User;
import com.nepse.portfolio.domain.UserSettings;
import com.nepse.portfolio.repository.UserRepository;
import com.nepse.portfolio.repository.UserSettingsRepository;
import com.nepse.portfolio.service.impl.UniverseStartupRunner;

import com.nepse.portfolio.domain.Alert;
import com.nepse.portfolio.repository.AlertRepository;
import com.nepse.portfolio.domain.AlertType;
import com.nepse.portfolio.domain.Stock;
import com.nepse.portfolio.repository.StockRepository;
import com.nepse.portfolio.domain.Trade;
import com.nepse.portfolio.repository.TradeRepository;
import com.nepse.portfolio.domain.TradeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Creates the demo/admin accounts and a small sample portfolio at startup.
 *
 * <p>The security universe itself is imported from NEPSE by
 * {@link com.nepse.portfolio.service.impl.UniverseStartupRunner}, which runs
 * first; this seeder only adds the accounts and demo trades that reference
 * those securities. Price history is not fabricated &mdash; it comes from the
 * real dataset via {@code POST /api/admin/market/backfill}.</p>
 */
@Component
@Order(2)
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final UserSettingsRepository settingsRepository;
    private final StockRepository stockRepository;
    private final TradeRepository tradeRepository;
    private final AlertRepository alertRepository;
    private final PasswordEncoder passwordEncoder;

    private final boolean demoUserEnabled;

    public DataSeeder(UserRepository userRepository,
                      UserSettingsRepository settingsRepository,
                      StockRepository stockRepository,
                      TradeRepository tradeRepository,
                      AlertRepository alertRepository,
                      PasswordEncoder passwordEncoder,
                      @Value("${app.seed.demo-user:true}") boolean demoUserEnabled) {
        this.userRepository = userRepository;
        this.settingsRepository = settingsRepository;
        this.stockRepository = stockRepository;
        this.tradeRepository = tradeRepository;
        this.alertRepository = alertRepository;
        this.passwordEncoder = passwordEncoder;
        this.demoUserEnabled = demoUserEnabled;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedUsers();
        seedDemoTrades();
        log.info("Seed check complete: {} stocks", stockRepository.count());
    }

    private void seedUsers() {
        if (!demoUserEnabled) {
            return;
        }
        User demo = userRepository.findByEmailIgnoreCase("demo@nepse.local")
                .orElseGet(() -> userRepository.save(new User(
                        "demo@nepse.local",
                        passwordEncoder.encode("demo1234"),
                        "Demo Investor",
                        Role.USER)));
        settingsRepository.findById(demo.getId())
                .orElseGet(() -> settingsRepository.save(new UserSettings(demo.getId())));

        User admin = userRepository.findByEmailIgnoreCase("admin@nepse.local")
                .orElseGet(() -> userRepository.save(new User(
                        "admin@nepse.local",
                        passwordEncoder.encode("admin1234"),
                        "Administrator",
                        Role.ADMIN)));
        settingsRepository.findById(admin.getId())
                .orElseGet(() -> settingsRepository.save(new UserSettings(admin.getId())));
    }

    private void seedDemoTrades() {
        if (!demoUserEnabled) {
            return;
        }
        User demo = userRepository.findByEmailIgnoreCase("demo@nepse.local").orElse(null);
        if (demo == null || !tradeRepository.findByUserIdOrderByTradeDateAscIdAsc(demo.getId()).isEmpty()) {
            return;
        }

        Map<String, Stock> stocks = stockRepository.findAll().stream()
                .collect(Collectors.toMap(Stock::getSymbol, Function.identity()));

        addTrade(demo, stocks, "NICA", TradeType.BUY, 120, "852.00", 165);
        addTrade(demo, stocks, "NBL", TradeType.BUY, 200, "405.00", 150);
        addTrade(demo, stocks, "SCB", TradeType.BUY, 40, "470.00", 135);
        addTrade(demo, stocks, "CZBIL", TradeType.BUY, 150, "480.00", 120);
        addTrade(demo, stocks, "HDL", TradeType.BUY, 15, "2280.00", 100);
        addTrade(demo, stocks, "NTC", TradeType.BUY, 25, "790.00", 80);
        addTrade(demo, stocks, "NBL", TradeType.SELL, 50, "385.00", 30);

        if (alertRepository.count() == 0) {
            Stock nica = stocks.get("NICA");
            Stock nbl = stocks.get("NBL");
            if (nica != null) {
                alertRepository.save(new Alert(demo.getId(), nica.getId(), AlertType.ABOVE, new BigDecimal("840.00")));
            }
            if (nbl != null) {
                alertRepository.save(new Alert(demo.getId(), nbl.getId(), AlertType.BELOW, new BigDecimal("360.00")));
            }
        }
        log.info("Seeded demo portfolio for demo@nepse.local");
    }

    private void addTrade(User user, Map<String, Stock> stocks, String symbol, TradeType type,
                          int quantity, String price, int daysAgo) {
        Stock stock = stocks.get(symbol);
        if (stock == null) {
            return;
        }
        Instant when = Instant.now().minus(daysAgo, ChronoUnit.DAYS);
        tradeRepository.save(new Trade(user.getId(), stock.getId(), type, quantity,
                new BigDecimal(price), BigDecimal.ZERO, BigDecimal.ZERO, when, "Demo trade"));
    }
}

package com.nepse.portfolio.seed;

import com.nepse.portfolio.alert.Alert;
import com.nepse.portfolio.alert.AlertRepository;
import com.nepse.portfolio.alert.AlertType;
import com.nepse.portfolio.market.PriceHistory;
import com.nepse.portfolio.market.PriceHistoryRepository;
import com.nepse.portfolio.market.Stock;
import com.nepse.portfolio.market.StockRepository;
import com.nepse.portfolio.portfolio.Trade;
import com.nepse.portfolio.portfolio.TradeRepository;
import com.nepse.portfolio.portfolio.TradeType;
import com.nepse.portfolio.user.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Kathmandu");

    private final UserRepository userRepository;
    private final UserSettingsRepository settingsRepository;
    private final StockRepository stockRepository;
    private final PriceHistoryRepository historyRepository;
    private final TradeRepository tradeRepository;
    private final AlertRepository alertRepository;
    private final PasswordEncoder passwordEncoder;

    private final boolean demoUserEnabled;
    private final int historyDays;

    public DataSeeder(UserRepository userRepository,
                      UserSettingsRepository settingsRepository,
                      StockRepository stockRepository,
                      PriceHistoryRepository historyRepository,
                      TradeRepository tradeRepository,
                      AlertRepository alertRepository,
                      PasswordEncoder passwordEncoder,
                      @Value("${app.seed.demo-user:true}") boolean demoUserEnabled,
                      @Value("${app.seed.price-history-days:260}") int historyDays) {
        this.userRepository = userRepository;
        this.settingsRepository = settingsRepository;
        this.stockRepository = stockRepository;
        this.historyRepository = historyRepository;
        this.tradeRepository = tradeRepository;
        this.alertRepository = alertRepository;
        this.passwordEncoder = passwordEncoder;
        this.demoUserEnabled = demoUserEnabled;
        this.historyDays = historyDays;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedUsers();
        seedPriceHistory();
        seedDemoTrades();
        log.info("Seed check complete: {} stocks, {} price bars",
                stockRepository.count(), historyRepository.count());
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

    private void seedPriceHistory() {
        List<Stock> stocks = stockRepository.findAll();
        List<LocalDate> dates = tradingDates(historyDays);
        List<PriceHistory> batch = new ArrayList<>();

        for (Stock stock : stocks) {
            if (historyRepository.countByStockId(stock.getId()) > 0) {
                continue;
            }
            Random random = new Random(stock.getSymbol().hashCode() * 31L + 17L);
            int n = dates.size();
            double[] closes = new double[n];
            double value = stock.getLtp().doubleValue() * (0.88 + random.nextDouble() * 0.2);
            for (int i = 0; i < n; i++) {
                double drift = 0.0006;
                double shock = random.nextGaussian() * 0.014;
                value = Math.max(1, value * (1 + drift + shock));
                closes[i] = value;
            }
            double scale = stock.getLtp().doubleValue() / Math.max(0.01, closes[n - 1]);
            for (int i = 0; i < n; i++) {
                closes[i] *= scale;
            }

            double previous = closes[0];
            for (int i = 0; i < n; i++) {
                LocalDate date = dates.get(i);
                double close = closes[i];
                double open = i == 0 ? previous * (1 + (random.nextDouble() - 0.5) * 0.01)
                        : previous * (1 + (random.nextDouble() - 0.5) * 0.012);
                double high = Math.max(open, close) * (1 + random.nextDouble() * 0.009);
                double low = Math.min(open, close) * (1 - random.nextDouble() * 0.009);
                long volume = Math.max(100, (long) (Math.max(1000, stock.getVolume())
                        * (0.35 + random.nextDouble() * 1.4)));

                if (i == n - 1) {
                    open = stock.getOpenPrice().doubleValue();
                    high = Math.max(stock.getHighPrice().doubleValue(), stock.getLtp().doubleValue());
                    low = Math.min(stock.getLowPrice().doubleValue(), stock.getLtp().doubleValue());
                    close = stock.getLtp().doubleValue();
                    volume = Math.max(1, stock.getVolume());
                }

                batch.add(new PriceHistory(stock.getId(), date,
                        money(open), money(high), money(low), money(close), volume));
                previous = close;
            }
        }
        if (!batch.isEmpty()) {
            historyRepository.saveAll(batch);
            log.info("Seeded {} price history bars", batch.size());
        }
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

    private List<LocalDate> tradingDates(int count) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate date = LocalDate.now(MARKET_ZONE);
        while (dates.size() < count) {
            DayOfWeek day = date.getDayOfWeek();
            if (day != DayOfWeek.FRIDAY && day != DayOfWeek.SATURDAY) {
                dates.add(date);
            }
            date = date.minusDays(1);
        }
        dates.sort(Comparator.naturalOrder());
        return dates;
    }

    private BigDecimal money(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}

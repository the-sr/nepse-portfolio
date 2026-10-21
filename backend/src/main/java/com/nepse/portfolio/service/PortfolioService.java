package com.nepse.portfolio.service;

import com.nepse.portfolio.domain.Trade;
import com.nepse.portfolio.domain.TradeType;
import com.nepse.portfolio.dto.portfolio.PortfolioDtos;
import com.nepse.portfolio.repository.TradeRepository;
import com.nepse.portfolio.utils.FifoCalculator;

import com.nepse.portfolio.common.ApiException;
import com.nepse.portfolio.domain.PriceHistory;
import com.nepse.portfolio.repository.PriceHistoryRepository;
import com.nepse.portfolio.domain.Stock;
import com.nepse.portfolio.repository.StockRepository;
import com.nepse.portfolio.dto.portfolio.PortfolioDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PortfolioService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Kathmandu");

    private final TradeRepository tradeRepository;
    private final StockRepository stockRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final FifoCalculator fifoCalculator = new FifoCalculator();

    public PortfolioService(TradeRepository tradeRepository,
                            StockRepository stockRepository,
                            PriceHistoryRepository priceHistoryRepository) {
        this.tradeRepository = tradeRepository;
        this.stockRepository = stockRepository;
        this.priceHistoryRepository = priceHistoryRepository;
    }

    @Transactional(readOnly = true)
    public PortfolioDto portfolio(Long userId) {
        List<Trade> trades = tradeRepository.findByUserIdOrderByTradeDateAscIdAsc(userId);
        List<HoldingDto> holdings = buildHoldings(trades);
        SummaryDto summary = buildSummary(holdings);

        if (summary.currentValue().signum() > 0) {
            holdings = holdings.stream()
                    .map(h -> new HoldingDto(h.stockId(), h.symbol(), h.companyName(), h.sector(),
                            h.quantity(), h.averageCost(), h.ltp(), h.prevClose(), h.invested(),
                            h.currentValue(), h.unrealizedProfitLoss(), h.unrealizedPercent(),
                            h.dayChange(), h.dayChangePercent(), h.realizedProfitLoss(),
                            percent(h.currentValue(), summary.currentValue())))
                    .toList();
        }

        List<AllocationSlice> allocation = holdings.stream()
                .sorted(Comparator.comparing(HoldingDto::currentValue).reversed())
                .map(h -> new AllocationSlice(h.symbol(), h.currentValue(), h.weightPercent()))
                .toList();

        Map<String, BigDecimal> bySector = holdings.stream()
                .collect(Collectors.groupingBy(
                        h -> h.sector() == null ? "Other" : h.sector(),
                        Collectors.reducing(BigDecimal.ZERO, HoldingDto::currentValue, BigDecimal::add)));

        List<AllocationSlice> sectorAllocation = bySector.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(e -> new AllocationSlice(e.getKey(), e.getValue(),
                        percent(e.getValue(), summary.currentValue())))
                .toList();

        return new PortfolioDto(summary, holdings, allocation, sectorAllocation);
    }

    @Transactional(readOnly = true)
    public SummaryDto summary(Long userId) {
        return buildSummary(buildHoldings(tradeRepository.findByUserIdOrderByTradeDateAscIdAsc(userId)));
    }

    @Transactional(readOnly = true)
    public List<HoldingDto> holdings(Long userId) {
        return portfolio(userId).holdings();
    }

    @Transactional(readOnly = true)
    public List<TradeDto> trades(Long userId) {
        List<Trade> trades = tradeRepository.findByUserIdOrderByTradeDateAscIdAsc(userId);
        Map<Long, Stock> stocks = stockMap(trades.stream().map(Trade::getStockId).distinct().toList());
        return trades.stream()
                .map(trade -> toDto(trade, stocks.get(trade.getStockId())))
                .toList();
    }

    @Transactional
    public TradeDto addTrade(Long userId, TradeRequest request) {
        Stock stock = stockRepository.findBySymbolIgnoreCase(request.symbol().trim())
                .orElseThrow(() -> ApiException.notFound("Stock " + request.symbol() + " not found"));

        if (!stock.isActive()) {
            throw ApiException.badRequest(stock.getSymbol()
                    + " is no longer listed as active on NEPSE, so new trades are blocked");
        }

        if (request.type() == TradeType.SELL) {
            int available = holdingsQuantity(userId, stock.getId());
            if (request.quantity() > available) {
                throw ApiException.badRequest("You only hold " + available + " shares of " + stock.getSymbol());
            }
        }

        Instant when = request.tradeDate() == null ? Instant.now() : request.tradeDate();
        Trade trade = new Trade(
                userId,
                stock.getId(),
                request.type(),
                request.quantity(),
                request.price(),
                request.brokerage(),
                request.tax(),
                when,
                request.notes()
        );
        return toDto(tradeRepository.save(trade), stock);
    }

    @Transactional
    public void deleteTrade(Long userId, Long tradeId) {
        Trade trade = tradeRepository.findByIdAndUserId(tradeId, userId)
                .orElseThrow(() -> ApiException.notFound("Trade not found"));
        tradeRepository.delete(trade);
        // Removing a buy could leave later sells unmatched; rebuild and validate the remaining history.
        List<Trade> remaining = tradeRepository.findByUserIdOrderByTradeDateAscIdAsc(userId);
        validateHistory(remaining);
    }

    @Transactional(readOnly = true)
    public List<HistoryPointDto> history(Long userId, int days) {
        List<Trade> trades = tradeRepository.findByUserIdOrderByTradeDateAscIdAsc(userId);
        if (trades.isEmpty()) {
            return List.of();
        }
        LocalDate today = LocalDate.now(MARKET_ZONE);
        LocalDate requestedFrom = today.minusDays(Math.max(7, Math.min(days, 730)));
        LocalDate earliestTrade = trades.stream()
                .map(trade -> trade.getTradeDate().atZone(MARKET_ZONE).toLocalDate())
                .min(LocalDate::compareTo)
                .orElse(requestedFrom);
        LocalDate from = earliestTrade.isAfter(requestedFrom)
                ? earliestTrade.minusDays(1)
                : requestedFrom;
        if (from.isBefore(requestedFrom)) {
            from = requestedFrom;
        }

        List<Long> stockIds = trades.stream().map(Trade::getStockId).distinct().toList();
        Map<Long, Stock> stocks = stockMap(stockIds);
        Map<Long, NavigableMap<LocalDate, BigDecimal>> closes = new HashMap<>();
        for (PriceHistory point : priceHistoryRepository
                .findByStockIdInAndTradeDateBetweenOrderByTradeDateAsc(stockIds, from.minusDays(10), today)) {
            closes.computeIfAbsent(point.getStockId(), k -> new TreeMap<>())
                    .put(point.getTradeDate(), point.getClosePrice());
        }

        Map<Long, List<Trade>> byStock = trades.stream().collect(Collectors.groupingBy(Trade::getStockId));
        List<HistoryPointDto> points = new ArrayList<>();

        for (LocalDate date = from; !date.isAfter(today); date = date.plusDays(1)) {
            BigDecimal value = BigDecimal.ZERO;
            BigDecimal invested = BigDecimal.ZERO;
            Instant cutoff = date.plusDays(1).atStartOfDay(MARKET_ZONE).toInstant();

            for (Map.Entry<Long, List<Trade>> entry : byStock.entrySet()) {
                List<Trade> upToDate = entry.getValue().stream()
                        .filter(t -> !t.getTradeDate().isAfter(cutoff))
                        .toList();
                if (upToDate.isEmpty()) {
                    continue;
                }
                FifoCalculator.Result result = fifoCalculator.calculate(upToDate);
                int quantity = result.openQuantity();
                if (quantity == 0) {
                    continue;
                }
                BigDecimal close = closeOn(closes.get(entry.getKey()), date,
                        stocks.get(entry.getKey()).getLtp());
                value = value.add(close.multiply(BigDecimal.valueOf(quantity)));
                invested = invested.add(result.invested());
            }

            points.add(new HistoryPointDto(date, money(value), money(invested), money(value.subtract(invested))));
        }
        return points;
    }

    private BigDecimal closeOn(NavigableMap<LocalDate, BigDecimal> closes, LocalDate date, BigDecimal fallback) {
        if (closes == null || closes.isEmpty()) {
            return fallback;
        }
        Map.Entry<LocalDate, BigDecimal> entry = closes.floorEntry(date);
        return entry == null ? fallback : entry.getValue();
    }

    private List<HoldingDto> buildHoldings(List<Trade> trades) {
        Map<Long, List<Trade>> byStock = trades.stream().collect(Collectors.groupingBy(Trade::getStockId));
        Map<Long, Stock> stocks = stockMap(byStock.keySet().stream().toList());
        List<HoldingDto> holdings = new ArrayList<>();

        for (Map.Entry<Long, List<Trade>> entry : byStock.entrySet()) {
            Stock stock = stocks.get(entry.getKey());
            if (stock == null) {
                continue;
            }
            FifoCalculator.Result result = fifoCalculator.calculate(entry.getValue());
            int quantity = result.openQuantity();
            if (quantity == 0) {
                continue;
            }
            BigDecimal averageCost = result.averageCost();
            BigDecimal invested = result.invested();
            BigDecimal currentValue = stock.getLtp().multiply(BigDecimal.valueOf(quantity));
            BigDecimal unrealized = currentValue.subtract(invested);
            BigDecimal dayChange = stock.getLtp().subtract(stock.getPrevClose())
                    .multiply(BigDecimal.valueOf(quantity));

            holdings.add(new HoldingDto(
                    stock.getId(),
                    stock.getSymbol(),
                    stock.getCompanyName(),
                    stock.getSector(),
                    quantity,
                    money(averageCost),
                    stock.getLtp(),
                    stock.getPrevClose(),
                    money(invested),
                    money(currentValue),
                    money(unrealized),
                    percent(unrealized, invested),
                    money(dayChange),
                    stock.getChangePercent(),
                    money(result.realizedProfitLoss()),
                    BigDecimal.ZERO
            ));
        }

        return holdings.stream()
                .sorted(Comparator.comparing(HoldingDto::currentValue).reversed())
                .toList();
    }

    private SummaryDto buildSummary(List<HoldingDto> holdings) {
        BigDecimal invested = BigDecimal.ZERO;
        BigDecimal value = BigDecimal.ZERO;
        BigDecimal realized = BigDecimal.ZERO;
        BigDecimal dayChange = BigDecimal.ZERO;

        for (HoldingDto h : holdings) {
            invested = invested.add(h.invested());
            value = value.add(h.currentValue());
            realized = realized.add(h.realizedProfitLoss());
            dayChange = dayChange.add(h.dayChange());
        }

        BigDecimal unrealized = value.subtract(invested);
        BigDecimal total = unrealized.add(realized);
        return new SummaryDto(
                money(invested),
                money(value),
                money(unrealized),
                money(realized),
                money(total),
                percent(total, invested),
                money(dayChange),
                percent(dayChange, value.subtract(dayChange)),
                holdings.size()
        );
    }

    private int holdingsQuantity(Long userId, Long stockId) {
        List<Trade> trades = tradeRepository.findByUserIdAndStockIdOrderByTradeDateAscIdAsc(userId, stockId);
        return fifoCalculator.calculate(trades).openQuantity();
    }

    private void validateHistory(List<Trade> trades) {
        Map<Long, List<Trade>> byStock = trades.stream().collect(Collectors.groupingBy(Trade::getStockId));
        for (List<Trade> stockTrades : byStock.values()) {
            int available = 0;
            for (Trade trade : stockTrades) {
                available += trade.getType() == TradeType.BUY ? trade.getQuantity() : -trade.getQuantity();
                if (available < 0) {
                    throw ApiException.badRequest("This change would leave a sell without enough bought shares");
                }
            }
        }
    }

    private Map<Long, Stock> stockMap(List<Long> ids) {
        return stockRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Stock::getId, Function.identity()));
    }

    private TradeDto toDto(Trade trade, Stock stock) {
        BigDecimal fees = trade.getFees();
        BigDecimal total = trade.getPrice().multiply(BigDecimal.valueOf(trade.getQuantity())).add(fees);
        return new TradeDto(
                trade.getId(),
                trade.getStockId(),
                stock == null ? "?" : stock.getSymbol(),
                stock == null ? "Unknown" : stock.getCompanyName(),
                trade.getType().name(),
                trade.getQuantity(),
                trade.getPrice(),
                trade.getBrokerage(),
                trade.getTax(),
                fees,
                money(total),
                trade.getTradeDate(),
                trade.getNotes()
        );
    }

    private BigDecimal percent(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return part.multiply(HUNDRED).divide(whole, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}

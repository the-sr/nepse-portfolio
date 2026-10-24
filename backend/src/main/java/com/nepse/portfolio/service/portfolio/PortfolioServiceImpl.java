package com.nepse.portfolio.service.portfolio;

import lombok.RequiredArgsConstructor;
import com.nepse.portfolio.domain.Trade;
import com.nepse.portfolio.domain.TradeType;
import com.nepse.portfolio.repository.TradeRepository;
import com.nepse.portfolio.utils.portfolio.FifoCalculator;
import com.nepse.portfolio.utils.portfolio.NepseCostEngine;
import com.nepse.portfolio.utils.portfolio.TradingRates;

import com.nepse.portfolio.exception.ApiException;
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
@RequiredArgsConstructor
public class PortfolioServiceImpl implements PortfolioService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Kathmandu");

    private final TradeRepository tradeRepository;
    private final StockRepository stockRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final TradingRatesService tradingRatesService;
    private final FifoCalculator fifoCalculator = new FifoCalculator();


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

        validateRequest(request);

        Instant when = request.tradeDate() == null ? Instant.now() : request.tradeDate();
        List<Trade> existingForStock =
                tradeRepository.findByUserIdAndStockIdOrderByTradeDateAscIdAsc(userId, stock.getId());

        if (request.type() == TradeType.SELL) {
            NepseCostEngine.WacLedger ledger = ledgerFor(existingForStock, tradingRatesService.effective(userId));
            int available = ledger.openQuantity();
            if (request.quantity() > available) {
                throw ApiException.badRequest("You only hold " + available + " shares of " + stock.getSymbol());
            }
        }

        Quote quote = quote(userId, stock, request, when, existingForStock);

        Trade trade = new Trade(
                userId,
                stock.getId(),
                request.type(),
                request.quantity(),
                request.price() == null ? BigDecimal.ZERO : request.price(),
                quote.brokerage(),
                quote.sebonFee(),
                quote.dpCharge(),
                quote.cgt(),
                when,
                request.notes()
        );
        return toDto(tradeRepository.save(trade), stock);
    }

    /** Same math as {@link #addTrade}, nothing saved — the trade form shows this. */
    @Transactional(readOnly = true)
    public ChargesPreviewDto previewTrade(Long userId, TradeRequest request) {
        Stock stock = stockRepository.findBySymbolIgnoreCase(request.symbol().trim())
                .orElseThrow(() -> ApiException.notFound("Stock " + request.symbol() + " not found"));
        validateRequest(request);
        Instant when = request.tradeDate() == null ? Instant.now() : request.tradeDate();
        List<Trade> existingForStock =
                tradeRepository.findByUserIdAndStockIdOrderByTradeDateAscIdAsc(userId, stock.getId());
        Quote quote = quote(userId, stock, request, when, existingForStock);

        return new ChargesPreviewDto(
                stock.getSymbol(),
                request.type().name(),
                request.quantity(),
                request.price() == null ? BigDecimal.ZERO : request.price(),
                quote.amount(),
                money(quote.brokerage()),
                money(quote.sebonFee()),
                money(quote.dpCharge()),
                money(quote.cgt()),
                quote.cgtRate(),
                quote.holdingDays(),
                money(quote.wacPerShare()),
                money(quote.totalCost()),
                money(quote.netProceeds()),
                money(quote.netProfit())
        );
    }

    @Transactional(readOnly = true)
    public CgtReportDto cgtReport(Long userId) {
        List<Trade> trades = tradeRepository.findByUserIdOrderByTradeDateAscIdAsc(userId);
        Map<Long, List<Trade>> byStock = trades.stream().collect(Collectors.groupingBy(Trade::getStockId));
        Map<Long, Stock> stocks = stockMap(byStock.keySet().stream().toList());
        TradingRates rates = tradingRatesService.effective(userId);

        BigDecimal totalGain = BigDecimal.ZERO;
        BigDecimal totalCgt = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;
        List<CgtLotDto> lots = new ArrayList<>();

        for (Map.Entry<Long, List<Trade>> entry : byStock.entrySet()) {
            List<Trade> stockTrades = entry.getValue();
            NepseCostEngine.WacLedger ledger = ledgerFor(stockTrades, rates);
            int quantity = ledger.openQuantity();
            Stock stock = stocks.get(entry.getKey());
            if (quantity <= 0 || stock == null) {
                continue;
            }
            BigDecimal wac = ledger.wacPerShare();
            BigDecimal ltp = stock.getLtp();
            BigDecimal amount = ltp.multiply(BigDecimal.valueOf(quantity));
            // Selling everything today: sell-side fees at current rates, DP once.
            BigDecimal fees = NepseCostEngine.commission(amount, rates)
                    .add(NepseCostEngine.sebonFee(amount, rates))
                    .add(rates.dpChargePerScripPerDay() == null ? BigDecimal.ZERO : rates.dpChargePerScripPerDay());
            BigDecimal invested = wac.multiply(BigDecimal.valueOf(quantity));
            BigDecimal gain = amount.subtract(fees).subtract(invested);
            int holding = ledger.earliestLotDate() == null ? 0
                    : NepseCostEngine.holdingDays(ledger.earliestLotDate(), Instant.now(), MARKET_ZONE);
            BigDecimal rate = NepseCostEngine.cgtRate(holding, rates);
            BigDecimal cgt = NepseCostEngine.cgtTax(gain, rate);
            BigDecimal net = gain.subtract(cgt);

            totalGain = totalGain.add(gain);
            totalCgt = totalCgt.add(cgt);
            totalNet = totalNet.add(net);

            lots.add(new CgtLotDto(
                    stock.getId(),
                    stock.getSymbol(),
                    stock.getCompanyName(),
                    quantity,
                    money(wac),
                    money(invested),
                    ltp,
                    money(gain),
                    money(fees),
                    rate,
                    money(cgt),
                    money(net),
                    ledger.earliestLotDate(),
                    holding,
                    holding <= NepseCostEngine.CGT_SHORT_MAX_DAYS ? "short" : "long"
            ));
        }

        return new CgtReportDto(money(totalGain), money(totalCgt), money(totalNet), lots);
    }

    /** Fee/tax snapshot for one pending trade against the user's rate schedule. */
    private record Quote(
            BigDecimal amount,
            BigDecimal brokerage,
            BigDecimal sebonFee,
            BigDecimal dpCharge,
            BigDecimal cgt,
            BigDecimal cgtRate,
            int holdingDays,
            BigDecimal wacPerShare,
            BigDecimal totalCost,
            BigDecimal netProceeds,
            BigDecimal netProfit
    ) {
    }

    private void validateRequest(TradeRequest request) {
        BigDecimal price = request.price();
        switch (request.type()) {
            case BONUS -> {
                if (price != null && price.signum() > 0) {
                    throw ApiException.badRequest(
                            "Bonus shares are free — leave the price at zero and note any TDS in the notes field");
                }
            }
            case IPO, FPO, RIGHT -> {
                if (price == null || price.signum() <= 0) {
                    throw ApiException.badRequest(
                            request.type() + " allotments need the offer/subscription price");
                }
            }
            case BUY, SELL, AUCTION -> {
                if (price == null || price.signum() <= 0) {
                    throw ApiException.badRequest(request.type() + " trades need a price");
                }
            }
        }
    }

    private Quote quote(Long userId, Stock stock, TradeRequest request, Instant when,
                        List<Trade> existingForStock) {
        TradingRates rates = tradingRatesService.effective(userId);
        TradeType type = request.type();
        BigDecimal price = request.price() == null ? BigDecimal.ZERO : request.price();
        BigDecimal amount = price.multiply(BigDecimal.valueOf(request.quantity()));

        BigDecimal brokerage;
        BigDecimal sebon;
        BigDecimal dp = BigDecimal.ZERO;
        BigDecimal cgt = BigDecimal.ZERO;
        BigDecimal cgtRate = BigDecimal.ZERO;
        int holdingDays = 0;
        BigDecimal wac = BigDecimal.ZERO;
        BigDecimal totalCost = amount.add(cgt);
        BigDecimal netProceeds = BigDecimal.ZERO;
        BigDecimal netProfit = BigDecimal.ZERO;

        if (type == TradeType.SELL) {
            brokerage = request.brokerage() == null
                    ? NepseCostEngine.commission(amount, rates) : request.brokerage();
            sebon = request.sebonFee() == null
                    ? NepseCostEngine.sebonFee(amount, rates) : request.sebonFee();
            dp = request.dpCharge() == null ? dpChargeFor(when, existingForStock, rates) : request.dpCharge();

            NepseCostEngine.WacLedger ledger = ledgerFor(existingForStock, rates);
            Trade pending = new Trade(userId, stock.getId(), type, request.quantity(), price,
                    brokerage, sebon, dp, BigDecimal.ZERO, when, null);
            NepseCostEngine.SellCosting costing = ledger.addSell(pending, rates, MARKET_ZONE);

            cgt = request.tax() == null ? costing.cgt() : request.tax();
            cgtRate = costing.cgtRate();
            holdingDays = costing.holdingDays();
            wac = costing.wacPerShare();
            netProceeds = amount.subtract(brokerage).subtract(sebon).subtract(dp);
            netProfit = netProceeds.subtract(cgt)
                    .subtract(costing.wacPerShare().multiply(BigDecimal.valueOf(request.quantity())));
            totalCost = netProfit;
        } else if (NepseCostEngine.chargesEntryFees(type)) {
            NepseCostEngine.BuyCosting costing = NepseCostEngine.costBuy(type, price, request.quantity(), rates);
            brokerage = request.brokerage() == null ? costing.commission() : request.brokerage();
            sebon = request.sebonFee() == null ? costing.sebonFee() : request.sebonFee();
            totalCost = amount.add(brokerage).add(sebon);
        } else {
            // IPO/FPO/RIGHT/BONUS allotments legitimately carry no fees.
            brokerage = request.brokerage() == null ? BigDecimal.ZERO : request.brokerage();
            sebon = request.sebonFee() == null ? BigDecimal.ZERO : request.sebonFee();
            totalCost = amount;
        }

        return new Quote(amount, brokerage, sebon, dp, cgt, cgtRate, holdingDays,
                wac, totalCost, netProceeds, netProfit);
    }

    /** The DP charge hits once per scrip per day, no matter how many sells. */
    private BigDecimal dpChargeFor(Instant when, List<Trade> existingForStock, TradingRates rates) {
        BigDecimal rate = rates.dpChargePerScripPerDay() == null ? BigDecimal.ZERO : rates.dpChargePerScripPerDay();
        LocalDate day = LocalDate.ofInstant(when, MARKET_ZONE);
        boolean soldSameDay = existingForStock.stream()
                .filter(t -> t.getType() == TradeType.SELL)
                .anyMatch(t -> LocalDate.ofInstant(t.getTradeDate(), MARKET_ZONE).equals(day));
        return soldSameDay ? BigDecimal.ZERO : rate;
    }

    private NepseCostEngine.WacLedger ledgerFor(List<Trade> trades, TradingRates rates) {
        NepseCostEngine.WacLedger ledger = new NepseCostEngine.WacLedger();
        for (Trade trade : trades) {
            if (NepseCostEngine.increasesQuantity(trade.getType())) {
                ledger.addBuy(trade, rates);
            } else {
                ledger.addSell(trade, rates, MARKET_ZONE);
            }
        }
        return ledger;
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
                available += NepseCostEngine.increasesQuantity(trade.getType())
                        ? trade.getQuantity() : -trade.getQuantity();
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
        BigDecimal amount = trade.getPrice().multiply(BigDecimal.valueOf(trade.getQuantity()));
        boolean sell = trade.getType() == TradeType.SELL;
        // Buys cost cash; sells return it. Show the signed cash movement either way.
        BigDecimal total = sell
                ? amount.subtract(fees).subtract(trade.getTax())
                : amount.add(fees);
        return new TradeDto(
                trade.getId(),
                trade.getStockId(),
                stock == null ? "?" : stock.getSymbol(),
                stock == null ? "Unknown" : stock.getCompanyName(),
                trade.getType().name(),
                trade.getQuantity(),
                trade.getPrice(),
                trade.getBrokerage(),
                trade.getSebonFee(),
                trade.getDpCharge(),
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

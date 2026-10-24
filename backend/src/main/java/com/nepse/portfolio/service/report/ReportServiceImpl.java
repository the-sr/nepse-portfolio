package com.nepse.portfolio.service.report;

import lombok.RequiredArgsConstructor;
import com.nepse.portfolio.service.report.ReportService;

import com.nepse.portfolio.domain.Stock;
import com.nepse.portfolio.repository.StockRepository;
import com.nepse.portfolio.utils.portfolio.FifoCalculator;
import com.nepse.portfolio.domain.Trade;
import com.nepse.portfolio.repository.TradeRepository;
import com.nepse.portfolio.dto.report.ReportDtos.RealizedGainRow;
import com.nepse.portfolio.dto.report.ReportDtos.RealizedGainsReport;
import com.nepse.portfolio.service.portfolio.TradingRatesService;
import com.nepse.portfolio.utils.portfolio.NepseCostEngine;
import com.nepse.portfolio.utils.portfolio.TradingRates;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Kathmandu");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final TradeRepository tradeRepository;
    private final StockRepository stockRepository;
    private final TradingRatesService tradingRatesService;
    private final FifoCalculator fifoCalculator = new FifoCalculator();


    @Transactional(readOnly = true)
    public RealizedGainsReport realizedGains(Long userId, LocalDate from, LocalDate to) {
        LocalDate start = from == null ? LocalDate.now(MARKET_ZONE).minusYears(1) : from;
        LocalDate end = to == null ? LocalDate.now(MARKET_ZONE) : to;
        if (start.isAfter(end)) {
            LocalDate swap = start;
            start = end;
            end = swap;
        }

        TradingRates rates = tradingRatesService.effective(userId);
        List<Trade> trades = tradeRepository.findByUserIdOrderByTradeDateAscIdAsc(userId);
        Map<Long, Stock> stocks = stockRepository.findAllById(
                        trades.stream().map(Trade::getStockId).distinct().toList())
                .stream().collect(Collectors.toMap(Stock::getId, Function.identity()));

        List<RealizedGainRow> rows = new ArrayList<>();
        for (Map.Entry<Long, List<Trade>> entry : trades.stream()
                .collect(Collectors.groupingBy(Trade::getStockId)).entrySet()) {
            Stock stock = stocks.get(entry.getKey());
            if (stock == null) {
                continue;
            }
            FifoCalculator.Result result = fifoCalculator.calculate(entry.getValue());
            for (FifoCalculator.RealizedSale sale : result.sales()) {
                LocalDate sold = sale.soldAt().atZone(MARKET_ZONE).toLocalDate();
                if (sold.isBefore(start) || sold.isAfter(end)) {
                    continue;
                }
                LocalDate bought = sale.acquiredAt().atZone(MARKET_ZONE).toLocalDate();
                long holdingDays = Math.max(0, ChronoUnit.DAYS.between(bought, sold));
                BigDecimal quantity = BigDecimal.valueOf(sale.quantity());
                BigDecimal costBasis = sale.costPerShare().multiply(quantity);
                BigDecimal proceeds = sale.sellPricePerShare().multiply(quantity);
                BigDecimal profit = sale.profitLoss();
                BigDecimal rate = NepseCostEngine.cgtRate((int) holdingDays, rates);
                BigDecimal tax = profit.signum() > 0
                        ? profit.multiply(rate).divide(HUNDRED, 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

                rows.add(new RealizedGainRow(
                        stock.getSymbol(),
                        stock.getCompanyName(),
                        sale.quantity(),
                        bought,
                        sold,
                        holdingDays,
                        money(costBasis),
                        money(proceeds),
                        money(profit),
                        rate,
                        tax
                ));
            }
        }

        rows.sort(Comparator.comparing(RealizedGainRow::sellDate).reversed());

        BigDecimal totalCost = rows.stream().map(RealizedGainRow::costBasis)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalProceeds = rows.stream().map(RealizedGainRow::proceeds)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalProfit = rows.stream().map(RealizedGainRow::profitLoss)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalTax = rows.stream().map(RealizedGainRow::estimatedTax)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new RealizedGainsReport(start, end, money(totalCost), money(totalProceeds),
                money(totalProfit), money(totalTax), rows.size(), rows);
    }

    public String toCsv(RealizedGainsReport report) {
        StringBuilder csv = new StringBuilder();
        csv.append("symbol,company,quantity,buy_date,sell_date,holding_days,cost_basis,proceeds,profit_loss,tax_rate_percent,estimated_tax\n");
        for (RealizedGainRow row : report.rows()) {
            csv.append(escape(row.symbol())).append(',')
                    .append(escape(row.companyName())).append(',')
                    .append(row.quantity()).append(',')
                    .append(row.buyDate()).append(',')
                    .append(row.sellDate()).append(',')
                    .append(row.holdingDays()).append(',')
                    .append(row.costBasis()).append(',')
                    .append(row.proceeds()).append(',')
                    .append(row.profitLoss()).append(',')
                    .append(row.taxRatePercent()).append(',')
                    .append(row.estimatedTax()).append('\n');
        }
        return csv.toString();
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}

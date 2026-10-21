package com.nepse.portfolio.service;

import com.nepse.portfolio.domain.Stock;
import com.nepse.portfolio.dto.market.MarketDtos;
import com.nepse.portfolio.repository.PriceHistoryRepository;
import com.nepse.portfolio.repository.StockRepository;

import com.nepse.portfolio.common.ApiException;
import com.nepse.portfolio.dto.market.MarketDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MarketService {

    private final StockRepository stockRepository;
    private final PriceHistoryRepository historyRepository;

    public MarketService(StockRepository stockRepository, PriceHistoryRepository historyRepository) {
        this.stockRepository = stockRepository;
        this.historyRepository = historyRepository;
    }

    @Transactional(readOnly = true)
    public List<StockDto> list(String query, String sector, boolean includeInactive) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String s = sector == null ? "" : sector.trim();
        return stockRepository.findAllByOrderBySymbolAsc().stream()
                .filter(stock -> includeInactive || stock.isActive())
                .filter(stock -> q.isEmpty()
                        || stock.getSymbol().toLowerCase(Locale.ROOT).contains(q)
                        || stock.getCompanyName().toLowerCase(Locale.ROOT).contains(q))
                .filter(stock -> s.isEmpty() || (stock.getSector() != null
                        && stock.getSector().equalsIgnoreCase(s)))
                .map(StockDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Stock stock(String symbol) {
        return stockRepository.findBySymbolIgnoreCase(symbol)
                .orElseThrow(() -> ApiException.notFound("Stock " + symbol + " not found"));
    }

    @Transactional(readOnly = true)
    public StockDetailDto detail(String symbol, String range) {
        Stock stock = stock(symbol);
        LocalDate to = LocalDate.now();
        LocalDate from = switch (range == null ? "6M" : range.toUpperCase(Locale.ROOT)) {
            case "1M" -> to.minusDays(30);
            case "3M" -> to.minusDays(90);
            case "1Y" -> to.minusDays(365);
            case "ALL" -> to.minusYears(10);
            default -> to.minusDays(180);
        };
        List<HistoryPoint> points = historyRepository
                .findByStockIdAndTradeDateBetweenOrderByTradeDateAsc(stock.getId(), from, to)
                .stream()
                .map(HistoryPoint::from)
                .toList();
        return new StockDetailDto(StockDto.from(stock), points);
    }

    @Transactional(readOnly = true)
    public MoversDto movers() {
        // The dashboard widget is about tradable equities; promoter shares and
        // debentures are extremely illiquid and would dominate the % rankings.
        List<StockDto> all = stockRepository.findAllByOrderBySymbolAsc().stream()
                .filter(Stock::isActive)
                .filter(stock -> "Equity".equalsIgnoreCase(stock.getInstrumentType()))
                .filter(stock -> stock.getLtp() != null && stock.getLtp().signum() > 0)
                .map(StockDto::from)
                .toList();
        List<StockDto> gainers = all.stream()
                .filter(s -> s.changePercent().signum() > 0)
                .sorted(Comparator.comparing(StockDto::changePercent).reversed())
                .limit(5)
                .toList();
        List<StockDto> losers = all.stream()
                .filter(s -> s.changePercent().signum() < 0)
                .sorted(Comparator.comparing(StockDto::changePercent))
                .limit(5)
                .toList();
        List<StockDto> active = all.stream()
                .sorted(Comparator.comparing(StockDto::turnover).reversed())
                .limit(5)
                .toList();
        return new MoversDto(gainers, losers, active);
    }

    @Transactional(readOnly = true)
    public List<SectorPerformanceDto> sectors() {
        Map<String, List<Stock>> bySector = stockRepository.findAllByOrderBySymbolAsc().stream()
                .filter(Stock::isActive)
                .filter(stock -> stock.getLtp() != null && stock.getLtp().signum() > 0)
                .filter(stock -> stock.getSector() != null)
                .collect(Collectors.groupingBy(Stock::getSector));

        return bySector.entrySet().stream()
                .map(entry -> {
                    List<Stock> stocks = entry.getValue();
                    BigDecimal avg = stocks.stream()
                            .map(Stock::getChangePercent)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(BigDecimal.valueOf(stocks.size()), 2, RoundingMode.HALF_UP);
                    BigDecimal turnover = stocks.stream()
                            .map(Stock::getTurnover)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new SectorPerformanceDto(entry.getKey(), stocks.size(), avg, turnover);
                })
                .sorted(Comparator.comparing(SectorPerformanceDto::avgChangePercent).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> sectorsList() {
        return stockRepository.findAllByOrderBySymbolAsc().stream()
                .filter(Stock::isActive)
                .map(Stock::getSector)
                .filter(sector -> sector != null && !sector.isBlank())
                .distinct()
                .sorted()
                .toList();
    }
}

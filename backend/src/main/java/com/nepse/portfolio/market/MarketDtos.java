package com.nepse.portfolio.market;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class MarketDtos {

    private MarketDtos() {
    }

    public record StockDto(
            Long id,
            String symbol,
            String companyName,
            String sector,
            BigDecimal ltp,
            BigDecimal prevClose,
            BigDecimal openPrice,
            BigDecimal highPrice,
            BigDecimal lowPrice,
            long volume,
            BigDecimal turnover,
            BigDecimal change,
            BigDecimal changePercent,
            Instant lastUpdated,
            String source
    ) {
        public static StockDto from(Stock stock) {
            return new StockDto(
                    stock.getId(),
                    stock.getSymbol(),
                    stock.getCompanyName(),
                    stock.getSector(),
                    stock.getLtp(),
                    stock.getPrevClose(),
                    stock.getOpenPrice(),
                    stock.getHighPrice(),
                    stock.getLowPrice(),
                    stock.getVolume(),
                    stock.getTurnover(),
                    stock.getChange(),
                    stock.getChangePercent(),
                    stock.getLastUpdated(),
                    stock.getSource()
            );
        }
    }

    public record HistoryPoint(
            LocalDate date,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            long volume
    ) {
        public static HistoryPoint from(PriceHistory h) {
            return new HistoryPoint(h.getTradeDate(), h.getOpenPrice(), h.getHighPrice(),
                    h.getLowPrice(), h.getClosePrice(), h.getVolume());
        }
    }

    public record StockDetailDto(StockDto stock, List<HistoryPoint> history) {
    }

    public record MoversDto(List<StockDto> gainers, List<StockDto> losers, List<StockDto> mostActive) {
    }

    public record SectorPerformanceDto(String sector, long stocks, BigDecimal avgChangePercent,
                                       BigDecimal turnover) {
    }
}

package com.nepse.portfolio.repository;

import com.nepse.portfolio.domain.PriceHistory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {

    Optional<PriceHistory> findByStockIdAndTradeDate(Long stockId, LocalDate tradeDate);

    List<PriceHistory> findByStockIdAndTradeDateBetweenOrderByTradeDateAsc(
            Long stockId, LocalDate from, LocalDate to);

    List<PriceHistory> findByStockIdInAndTradeDateBetweenOrderByTradeDateAsc(
            List<Long> stockIds, LocalDate from, LocalDate to);

    List<PriceHistory> findTop400ByStockIdOrderByTradeDateDesc(Long stockId);

    @Query("select max(h.tradeDate) from PriceHistory h where h.stockId = :stockId")
    Optional<LocalDate> findLatestDate(@Param("stockId") Long stockId);

    /** Newest bar across the whole table - the heartbeat of stored history. */
    @Query("select max(h.tradeDate) from PriceHistory h")
    Optional<LocalDate> findLatestDateOverall();

    long countByStockId(Long stockId);
}

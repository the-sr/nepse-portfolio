package com.nepse.portfolio.portfolio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    List<Trade> findByUserIdOrderByTradeDateAscIdAsc(Long userId);

    List<Trade> findByUserIdAndStockIdOrderByTradeDateAscIdAsc(Long userId, Long stockId);

    Optional<Trade> findByIdAndUserId(Long id, Long userId);

    void deleteByUserIdAndStockId(Long userId, Long stockId);
}

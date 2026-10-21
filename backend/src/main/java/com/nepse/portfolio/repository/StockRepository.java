package com.nepse.portfolio.repository;

import com.nepse.portfolio.domain.Stock;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {

    Optional<Stock> findBySymbolIgnoreCase(String symbol);

    boolean existsBySymbolIgnoreCase(String symbol);

    List<Stock> findAllByOrderBySymbolAsc();

    List<Stock> findBySectorIgnoreCaseOrderBySymbolAsc(String sector);

    long countBySource(String source);

    long countByActiveTrue();
}

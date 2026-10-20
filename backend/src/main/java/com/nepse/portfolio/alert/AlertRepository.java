package com.nepse.portfolio.alert;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Alert> findByActiveTrueAndStockIdIn(List<Long> stockIds);

    Optional<Alert> findByIdAndUserId(Long id, Long userId);
}

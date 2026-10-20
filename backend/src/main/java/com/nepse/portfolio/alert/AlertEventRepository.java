package com.nepse.portfolio.alert;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertEventRepository extends JpaRepository<AlertEvent, Long> {

    List<AlertEvent> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);
}

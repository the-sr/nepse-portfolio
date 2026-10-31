package com.nepse.portfolio.repository;

import com.nepse.portfolio.model.AlertEvent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertEventRepository extends JpaRepository<AlertEvent, Long> {

    List<AlertEvent> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);
}

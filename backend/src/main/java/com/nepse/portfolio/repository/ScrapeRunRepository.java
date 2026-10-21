package com.nepse.portfolio.repository;

import com.nepse.portfolio.domain.ScrapeRun;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScrapeRunRepository extends JpaRepository<ScrapeRun, Long> {

    Optional<ScrapeRun> findTopByOrderByStartedAtDesc();

    List<ScrapeRun> findTop20ByOrderByStartedAtDesc();
}

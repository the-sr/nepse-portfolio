package com.nepse.portfolio.repository;

import com.nepse.portfolio.domain.ContactMessage;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactRepository extends JpaRepository<ContactMessage, Long> {
}

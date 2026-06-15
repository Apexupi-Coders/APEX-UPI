package com.pspswitch.rules.repository;

import com.pspswitch.rules.entity.ValidationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ValidationLogRepository extends JpaRepository<ValidationLog, UUID> {
}

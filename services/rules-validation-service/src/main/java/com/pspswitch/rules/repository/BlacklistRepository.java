package com.pspswitch.rules.repository;

import com.pspswitch.rules.entity.Blacklist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BlacklistRepository extends JpaRepository<Blacklist, UUID> {
    boolean existsByIdentifier(String identifier);
}

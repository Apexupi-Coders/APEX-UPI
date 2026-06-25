package com.pspswitch.rules.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "blacklist")
public class Blacklist {

    @Id
    @GeneratedValue
    private UUID id;

    private String identifier;
    private String identifierType;
    private String reason;
    private LocalDateTime createdAt = LocalDateTime.now();
}

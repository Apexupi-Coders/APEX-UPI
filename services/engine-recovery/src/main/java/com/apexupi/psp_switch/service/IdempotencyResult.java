package com.apexupi.psp_switch.service;

public record IdempotencyResult(String txnId, String status) {}


package com.apexupi.psp_switch.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.springframework.stereotype.Service;

@Service
public class CryptoService {

    public String maskUpi(String upiId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(upiId.getBytes(StandardCharsets.UTF_8));
            StringBuilder masked = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                masked.append(String.format("%02x", hash[i]));
            }
            return masked.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public String getPiiFingerprint(String payer, String payee, double amount) {
        String input = payer + "|" + payee + "|" + amount;
        return maskUpi(input);
    }
}


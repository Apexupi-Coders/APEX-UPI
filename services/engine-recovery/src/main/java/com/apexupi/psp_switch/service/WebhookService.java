package com.apexupi.psp_switch.service;

import org.springframework.stereotype.Service;

@Service
public class WebhookService {

    public void notifyClient(String txnId, String status) {

        // Simulated webhook call
        System.out.println("🔔 Webhook sent → txnId: " + txnId + " status: " + status);
    }
}
package com.apexupi.psp_switch.queue;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.springframework.stereotype.Component;

@Component
public class DeadLetterQueue {

    private final BlockingQueue<String> dlq = new LinkedBlockingQueue<>();

    public void sendToDLQ(String txnId) {
        dlq.offer(txnId);
    }

    public String read() throws InterruptedException {
        return dlq.take();
    }
}
package com.apexupi.psp_switch.queue;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.springframework.stereotype.Component;

@Component
public class PaymentQueue {

    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>(5);

    public boolean publish(String txnId) {
    return queue.offer(txnId); // returns false if full
}

    public String consume() throws InterruptedException {
        return queue.take();
    }
}



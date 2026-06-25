package com.apexupi.psp_switch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling    // ← ADD THIS so @Scheduled works in Phase 8
public class PspSwitchApplication {

    public static void main(String[] args) {
        SpringApplication.run(PspSwitchApplication.class, args);
    }
}

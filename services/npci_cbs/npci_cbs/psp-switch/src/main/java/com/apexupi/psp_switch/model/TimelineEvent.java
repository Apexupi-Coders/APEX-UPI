package com.apexupi.psp_switch.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimelineEvent {
    private String eventName;
    private LocalDateTime timestamp;
}


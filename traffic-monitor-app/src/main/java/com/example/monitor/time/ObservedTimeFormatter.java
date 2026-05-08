package com.example.monitor.time;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public class ObservedTimeFormatter {
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Jerusalem");

    private static final DateTimeFormatter DISPLAY_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy - HH:mm:ss.SSS")
                    .withZone(DISPLAY_ZONE);

    public String format(Instant instant) {
        if (instant == null) {
            return "";
        }

        return DISPLAY_FORMATTER.format(instant);
    }
}

package com.example.monitor.api;

import com.example.monitor.persistence.HistoryPage;
import com.example.monitor.persistence.HistoryQuery;
import com.example.monitor.persistence.MessageArchiveRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
public class HistoryController {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 500;

    private final MessageArchiveRepository messageArchiveRepository;

    public HistoryController(MessageArchiveRepository messageArchiveRepository) {
        this.messageArchiveRepository = messageArchiveRepository;
    }

    @GetMapping("/api/messages/history")
    public HistoryResponse history(
            @RequestParam(name = "interfaceName", required = false) String interfaceName,
            @RequestParam(name = "messageType", required = false) String messageType,
            @RequestParam(name = "parseErrorOnly", defaultValue = "false") boolean parseErrorOnly,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "limit", defaultValue = "" + DEFAULT_LIMIT) int limit,
            @RequestParam(name = "offset", defaultValue = "0") int offset
    ) {
        int effectiveLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        int effectiveOffset = Math.max(offset, 0);

        HistoryQuery query = new HistoryQuery(
                interfaceName,
                messageType,
                parseErrorOnly,
                parseInstant(from, "from"),
                parseInstant(to, "to"),
                effectiveLimit,
                effectiveOffset
        );

        HistoryPage page = messageArchiveRepository.findHistory(query);
        return new HistoryResponse(page.items(), page.totalCount(), effectiveLimit, effectiveOffset);
    }

    private Instant parseInstant(String value, String paramName) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Instant.parse(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid " + paramName + ": must be an ISO-8601 instant, got '" + value + "'");
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleInvalidRequest(IllegalArgumentException e) {
        return e.getMessage();
    }
}

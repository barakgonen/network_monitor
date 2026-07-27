package com.example.monitor.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Shared "reject malformed query params with a 400 and the exception message" behavior for
 * controllers whose invalid-input signal is {@link IllegalArgumentException} (analytics/history
 * query parsing). Not applied globally: other controllers in this package intentionally handle
 * errors differently (e.g. returning a 200 with a success=false body), so this stays opt-in via
 * inheritance rather than a `@RestControllerAdvice`.
 */
abstract class AbstractBadRequestController {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleInvalidRequest(IllegalArgumentException e) {
        return e.getMessage();
    }
}

package com.pasheez.price;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns domain exceptions into RFC 9457 problem responses, so a caller gets a meaningful
 * status instead of a bare 500 with a stack-trace-shaped body.
 */
@RestControllerAdvice
public class PriceExceptionHandler {

    @ExceptionHandler(UnknownSymbolException.class)
    ProblemDetail onUnknownSymbol(UnknownSymbolException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Unknown symbol");
        return problem;
    }

    @ExceptionHandler(PriceUnavailableException.class)
    ProblemDetail onPriceUnavailable(PriceUnavailableException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problem.setTitle("Price temporarily unavailable");
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail onInvalidArgument(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid request");
        return problem;
    }
}

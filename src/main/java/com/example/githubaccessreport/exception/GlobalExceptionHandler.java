package com.example.githubaccessreport.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(GitHubApiException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleGitHubApiException(GitHubApiException ex, ServerWebExchange exchange) {
        HttpStatusCode status = ex.getStatusCode() != null ? ex.getStatusCode() : HttpStatus.BAD_GATEWAY;
        log.error("GitHub API call failed: {}", ex.getMessage());
        ErrorResponse body = ErrorResponse.of(
                status.value(),
                "GitHub API Error",
                ex.getMessage(),
                exchange.getRequest().getPath().value());
        return Mono.just(ResponseEntity.status(status).body(body));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleNotFound(ResourceNotFoundException ex, ServerWebExchange exchange) {
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                ex.getMessage(),
                exchange.getRequest().getPath().value());
        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(body));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleBadRequest(IllegalArgumentException ex, ServerWebExchange exchange) {
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                ex.getMessage(),
                exchange.getRequest().getPath().value());
        return Mono.just(ResponseEntity.badRequest().body(body));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleUnexpected(Exception ex, ServerWebExchange exchange) {
        log.error("Unexpected error handling request {}", exchange.getRequest().getPath().value(), ex);
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "An unexpected error occurred. Please try again later.",
                exchange.getRequest().getPath().value());
        return Mono.just(ResponseEntity.internalServerError().body(body));
    }
}

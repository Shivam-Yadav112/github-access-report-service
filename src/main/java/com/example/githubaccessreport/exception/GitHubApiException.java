package com.example.githubaccessreport.exception;

import org.springframework.http.HttpStatusCode;

/**
 * Raised when a call to the GitHub API fails in a way we can't transparently
 * recover from (auth failure, org/repo not found, rate limit exhausted after
 * retries, unexpected response, etc).
 */
public class GitHubApiException extends RuntimeException {

    private final HttpStatusCode statusCode;

    public GitHubApiException(String message, HttpStatusCode statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public GitHubApiException(String message, HttpStatusCode statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public HttpStatusCode getStatusCode() {
        return statusCode;
    }
}

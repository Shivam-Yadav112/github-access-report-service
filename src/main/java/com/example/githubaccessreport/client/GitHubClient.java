package com.example.githubaccessreport.client;

import com.example.githubaccessreport.config.GitHubProperties;
import com.example.githubaccessreport.exception.GitHubApiException;
import com.example.githubaccessreport.model.github.GitHubCollaborator;
import com.example.githubaccessreport.model.github.GitHubRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;

/**
 * Thin wrapper around GitHub's REST v3 API.
 *
 * <p>Two things matter for efficiency at scale here:
 * <ul>
 *   <li>Pagination is followed automatically via {@link #paginate}, expanding each
 *       org/repo into as many pages as it needs without the caller worrying about it.</li>
 *   <li>Callers control concurrency explicitly (see {@code AccessReportService}), so we
 *       fetch collaborators for many repositories in parallel instead of one at a time,
 *       while still respecting GitHub's rate limits via bounded concurrency + retry/backoff.</li>
 * </ul>
 */
@Component
public class GitHubClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubClient.class);

    private final WebClient webClient;
    private final GitHubProperties properties;

    public GitHubClient(WebClient gitHubWebClient, GitHubProperties properties) {
        this.webClient = gitHubWebClient;
        this.properties = properties;
    }

    /** Lists every (non-archived) repository belonging to the organization. */
    public Flux<GitHubRepo> listOrganizationRepos(String org) {
        return paginate(page -> webClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/orgs/{org}/repos")
                                .queryParam("per_page", properties.pageSize())
                                .queryParam("page", page)
                                .queryParam("type", "all")
                                .build(org)),
                GitHubRepo.class)
                .filter(repo -> !repo.archived());
    }

    /**
     * Lists every collaborator (direct, team-based, and outside collaborators)
     * on a repository, together with their effective permission level.
     */
    public Flux<GitHubCollaborator> listRepositoryCollaborators(String org, String repoName) {
        return paginate(page -> webClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/repos/{org}/{repo}/collaborators")
                                .queryParam("per_page", properties.pageSize())
                                .queryParam("page", page)
                                .queryParam("affiliation", "all")
                                .build(org, repoName)),
                GitHubCollaborator.class)
                .onErrorResume(GitHubApiException.class, ex -> {
                    // A single inaccessible/renamed repo shouldn't fail the whole report;
                    // log and continue with the rest of the organization.
                    log.warn("Skipping collaborators for {}/{}: {}", org, repoName, ex.getMessage());
                    return Flux.empty();
                });
    }

    /**
     * Generic page-following helper. Keeps requesting page N+1 until GitHub
     * returns a page with fewer than `per_page` items (i.e. the last page).
     * Pages for a single resource are fetched lazily/sequentially (we don't
     * know the total page count up front), but independent resources - e.g.
     * different repos' collaborator lists - can still be paginated fully in
     * parallel with each other; see how AccessReportService drives this.
     */
    private <T> Flux<T> paginate(java.util.function.IntFunction<WebClient.RequestHeadersSpec<?>> requestForPage,
                                  Class<T> type) {
        return fetchPage(requestForPage, type, 1);
    }

    private <T> Flux<T> fetchPage(java.util.function.IntFunction<WebClient.RequestHeadersSpec<?>> requestForPage,
                                   Class<T> type, int page) {
        return requestForPage.apply(page)
                .retrieve()
                .onStatus(status -> status.value() == HttpStatus.FORBIDDEN.value()
                                || status.value() == HttpStatus.TOO_MANY_REQUESTS.value(),
                        this::rateLimitError)
                .onStatus(org.springframework.http.HttpStatusCode::isError, this::apiError)
                .bodyToFlux(type)
                .collectList()
                .retryWhen(retryOnTransientError())
                .flatMapMany(items -> {
                    if (items.isEmpty()) {
                        return Flux.empty();
                    }
                    Flux<T> current = Flux.fromIterable(items);
                    boolean lastPage = items.size() < properties.pageSize();
                    return lastPage
                            ? current
                            : current.concatWith(Flux.defer(() -> fetchPage(requestForPage, type, page + 1)));
                });
    }

    private Mono<? extends Throwable> rateLimitError(org.springframework.web.reactive.function.client.ClientResponse response) {
        String remaining = response.headers().header("X-RateLimit-Remaining").stream().findFirst().orElse("?");
        String reset = response.headers().header("X-RateLimit-Reset").stream().findFirst().orElse("?");
        return response.createException().flatMap(ex -> Mono.error(new GitHubApiException(
                "GitHub rate limit hit (remaining=" + remaining + ", resetsAt=" + reset + ")",
                response.statusCode(), ex)));
    }

    private Mono<? extends Throwable> apiError(org.springframework.web.reactive.function.client.ClientResponse response) {
        return response.createException().flatMap(ex -> Mono.error(new GitHubApiException(
                "GitHub API returned " + response.statusCode() + ": " + ex.getMessage(),
                response.statusCode(), ex)));
    }

    private Retry retryOnTransientError() {
        return Retry.backoff(3, Duration.ofSeconds(2))
                .maxBackoff(Duration.ofSeconds(30))
                .filter(this::isRetryable)
                .onRetryExhaustedThrow((spec, signal) -> new GitHubApiException(
                        "Exceeded retries calling GitHub API: " + signal.failure().getMessage(),
                        HttpStatus.BAD_GATEWAY, signal.failure()));
    }

    private boolean isRetryable(Throwable throwable) {
        if (throwable instanceof GitHubApiException apiEx) {
            int status = apiEx.getStatusCode() != null ? apiEx.getStatusCode().value() : 0;
            // 403/429 = rate limited (worth backing off and retrying), 5xx = transient server error.
            return status == 403 || status == 429 || status >= 500;
        }
        return throwable instanceof WebClientResponseException.ServiceUnavailable
                || throwable instanceof java.io.IOException;
    }
}

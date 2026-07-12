package com.example.githubaccessreport.service;

import com.example.githubaccessreport.client.GitHubClient;
import com.example.githubaccessreport.config.GitHubProperties;
import com.example.githubaccessreport.model.github.GitHubCollaborator;
import com.example.githubaccessreport.model.github.GitHubRepo;
import com.example.githubaccessreport.model.report.AccessReportResponse;
import com.example.githubaccessreport.model.report.RepoAccess;
import com.example.githubaccessreport.model.report.UserAccessReport;
import com.github.benmanes.caffeine.cache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds the organization-wide user -> repository access report.
 *
 * <p>Repositories are listed once, then collaborators for every repository are
 * fetched concurrently (bounded by {@code github.max-concurrent-requests}) rather
 * than sequentially, which is what makes this practical for orgs with 100+ repos:
 * wall-clock time is roughly (repoCount / concurrency) round-trips instead of
 * repoCount round-trips.
 */
@Service
public class AccessReportService {

    private static final Logger log = LoggerFactory.getLogger(AccessReportService.class);

    private final GitHubClient gitHubClient;
    private final GitHubProperties properties;
    private final Cache<String, AccessReportResponse> cache;

    public AccessReportService(GitHubClient gitHubClient, GitHubProperties properties,
                                Cache<String, AccessReportResponse> cache) {
        this.gitHubClient = gitHubClient;
        this.properties = properties;
        this.cache = cache;
    }

    /** Returns the cached report if present, otherwise builds a fresh one and caches it. */
    public Mono<AccessReportResponse> getReport(String org) {
        AccessReportResponse cached = cache.getIfPresent(org);
        if (cached != null) {
            return Mono.just(cached);
        }
        return buildReport(org).doOnNext(report -> cache.put(org, report));
    }

    /** Forces a rebuild of the report, bypassing (and refreshing) the cache. */
    public Mono<AccessReportResponse> refreshReport(String org) {
        return buildReport(org).doOnNext(report -> cache.put(org, report));
    }

    private Mono<AccessReportResponse> buildReport(String org) {
        log.info("Building access report for org '{}'", org);
        Instant start = Instant.now();

        return gitHubClient.listOrganizationRepos(org)
                .collectList()
                .flatMap(repos -> reactor.core.publisher.Flux.fromIterable(repos)
                        // Bounded concurrency: fetch collaborators for many repos in parallel
                        // without opening one unbounded flood of requests at once.
                        .flatMap(repo -> collaboratorsForRepo(org, repo), properties.maxConcurrentRequests())
                        .collectList()
                        .map(perRepoAccess -> assembleReport(org, repos.size(), perRepoAccess)))
                .doOnNext(report -> log.info("Built access report for '{}': {} repos, {} users in {} ms",
                        org, report.repositoryCount(), report.userCount(),
                        java.time.Duration.between(start, Instant.now()).toMillis()));
    }

    /** Fetches collaborators for one repo and maps each to a (username, RepoAccess) pair. */
    private Mono<List<Map.Entry<String, RepoAccess>>> collaboratorsForRepo(String org, GitHubRepo repo) {
        return gitHubClient.listRepositoryCollaborators(org, repo.name())
                .map(collaborator -> toEntry(repo, collaborator))
                .collectList();
    }

    private Map.Entry<String, RepoAccess> toEntry(GitHubRepo repo, GitHubCollaborator collaborator) {
        String permission = collaborator.permissions() != null
                ? collaborator.permissions().highestPermission()
                : "unknown";
        RepoAccess access = new RepoAccess(repo.name(), permission, collaborator.roleName(), repo.htmlUrl());
        return Map.entry(collaborator.login(), access);
    }

    private AccessReportResponse assembleReport(String org, int repoCount,
                                                 List<List<Map.Entry<String, RepoAccess>>> perRepoAccess) {
        Map<String, List<RepoAccess>> byUser = perRepoAccess.stream()
                .flatMap(List::stream)
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

        List<UserAccessReport> users = byUser.entrySet().stream()
                .map(e -> new UserAccessReport(
                        e.getKey(),
                        "https://github.com/" + e.getKey(),
                        e.getValue().size(),
                        e.getValue().stream()
                                .sorted(Comparator.comparing(RepoAccess::repository))
                                .toList()))
                .sorted(Comparator.comparing(UserAccessReport::username, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return new AccessReportResponse(org, Instant.now(), repoCount, users.size(), users);
    }
}

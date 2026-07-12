package com.example.githubaccessreport.controller;

import com.example.githubaccessreport.exception.ResourceNotFoundException;
import com.example.githubaccessreport.model.report.AccessReportResponse;
import com.example.githubaccessreport.model.report.PagedResponse;
import com.example.githubaccessreport.model.report.RepoAccess;
import com.example.githubaccessreport.model.report.UserAccessReport;
import com.example.githubaccessreport.service.AccessReportService;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/orgs/{org}/access-report")
public class AccessReportController {

    private final AccessReportService accessReportService;

    public AccessReportController(AccessReportService accessReportService) {
        this.accessReportService = accessReportService;
    }

    /**
     * Full access report: every user in the org mapped to the repositories they can
     * access. Results are paginated on the `users` list since orgs can have 1000+ users.
     */
    @GetMapping
    public Mono<PagedResponse<UserAccessReport>> getAccessReport(
            @PathVariable String org,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) int size) {
        return accessReportService.getReport(org)
                .map(report -> PagedResponse.of(report.users(), page, size));
    }

    /** Same as {@link #getAccessReport} but without pagination metadata - the raw report. */
    @GetMapping("/full")
    public Mono<AccessReportResponse> getFullAccessReport(@PathVariable String org) {
        return accessReportService.getReport(org);
    }

    /** Repositories a single user can access, with their permission level on each. */
    @GetMapping("/users/{username}")
    public Mono<UserAccessReport> getUserAccess(@PathVariable String org, @PathVariable String username) {
        return accessReportService.getReport(org)
                .flatMap(report -> report.users().stream()
                        .filter(u -> u.username().equalsIgnoreCase(username))
                        .findFirst()
                        .map(Mono::just)
                        .orElseGet(() -> Mono.error(new ResourceNotFoundException(
                                "User '" + username + "' has no recorded access in org '" + org + "'"))));
    }

    /** Every user (and their permission level) with access to a single repository. */
    @GetMapping("/repos/{repo}")
    public Mono<List<UserWithPermission>> getRepoAccess(@PathVariable String org, @PathVariable String repo) {
        return accessReportService.getReport(org)
                .map(report -> report.users().stream()
                        .flatMap(user -> user.repositoryAccess().stream()
                                .filter(access -> access.repository().equalsIgnoreCase(repo))
                                .map(access -> new UserWithPermission(user.username(), access)))
                        .toList())
                .flatMap(list -> list.isEmpty()
                        ? Mono.error(new ResourceNotFoundException(
                                "Repository '" + repo + "' not found (or has no collaborators) in org '" + org + "'"))
                        : Mono.just(list));
    }

    /** Forces the cached report to be rebuilt from GitHub right now. */
    @PostMapping("/refresh")
    public Mono<AccessReportResponse> refresh(@PathVariable String org) {
        return accessReportService.refreshReport(org);
    }

    public record UserWithPermission(String username, RepoAccess access) {
    }
}

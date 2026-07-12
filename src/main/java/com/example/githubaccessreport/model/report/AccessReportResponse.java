package com.example.githubaccessreport.model.report;

import java.time.Instant;
import java.util.List;

/**
 * The full, aggregated access report for an organization: every user mapped
 * to the repositories they can access.
 */
public record AccessReportResponse(
        String organization,
        Instant generatedAt,
        int repositoryCount,
        int userCount,
        List<UserAccessReport> users
) {
}

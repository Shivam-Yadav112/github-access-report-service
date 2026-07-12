# GitHub Access Report Service

A Spring Boot service that connects to GitHub, inspects an organization's repositories
and their collaborators, and exposes a REST API that reports **which users have access
to which repositories**, and at what permission level.

Built with Spring WebFlux (reactive `WebClient`) so that, for orgs with 100+
repositories, collaborator lookups run **concurrently** instead of one request at a time.

---

## 1. How to run the project

### Prerequisites
- Java 17+
- Maven 3.8+ (or use the included `./mvnw` if you add the wrapper, or Docker — see below)
- A GitHub Personal Access Token (see [Authentication](#2-how-authentication-is-configured))

### Run locally
```bash
export GITHUB_TOKEN=ghp_your_token_here
mvn clean package
java -jar target/github-access-report.jar
```
The service starts on `http://localhost:8080`.

### Run with Docker
```bash
docker build -t github-access-report .
docker run -p 8080:8080 -e GITHUB_TOKEN=ghp_your_token_here github-access-report
```

### Run tests
```bash
mvn test
```

### Configuration
All settings live in `src/main/resources/application.yml` and can be overridden via
environment variables:

| Property                          | Env var                        | Default                  | Description                                                        |
|-----------------------------------|---------------------------------|---------------------------|----------------------------------------------------------------------|
| `github.api-base-url`             | `GITHUB_API_BASE_URL`          | `https://api.github.com` | GitHub REST API base URL (override for GitHub Enterprise Server)   |
| `github.token`                    | `GITHUB_TOKEN`                 | *(required)*              | Auth token used on every GitHub API call                           |
| `github.page-size`                | `GITHUB_PAGE_SIZE`              | `100`                     | Items per page when paginating GitHub list endpoints (max 100)     |
| `github.max-concurrent-requests`  | `GITHUB_MAX_CONCURRENT_REQUESTS`| `10`                      | Max collaborator-list calls in flight at once                      |
| `github.cache-ttl-minutes`        | `GITHUB_CACHE_TTL_MINUTES`      | `15`                      | How long a generated report is cached before being rebuilt          |

---

## 2. How authentication is configured

The service authenticates to GitHub using a **Personal Access Token (PAT)**, sent as a
`Bearer` token on every request (`Authorization: Bearer <token>`), configured via the
`GITHUB_TOKEN` environment variable — it is never hard-coded or committed.

### Creating a token
Use a **fine-grained PAT** scoped to the organization, with:
- Repository permissions → **Administration: Read-only** (needed to list collaborators
  and their permission levels) and **Metadata: Read-only**
- Organization permissions → **Members: Read-only**

Or a **classic PAT** with the `read:org` and `repo` scopes.

### Why a PAT and not a GitHub App?
A PAT is the simplest secure option for a service that reads data from a single
organization, and keeps this assignment self-contained. For a production, multi-tenant
version (reporting on many orgs, run on a schedule, etc.), a **GitHub App with an
installation token** would be the better choice: tokens are scoped per-installation,
short-lived, and don't tie access to one person's account. Swapping this in only
requires changing how the `Authorization` header is generated in `WebClientConfig`
(installation tokens are minted via a signed JWT exchange, then cached until they expire)
— the rest of the service (pagination, aggregation, endpoints) is unaffected.

The token needs **no write scopes** — this service is read-only against the GitHub API.

---

## 3. How to call the API endpoint

All endpoints are scoped to an organization by path variable.

### Get the full access report (paginated)
```
GET /api/v1/orgs/{org}/access-report?page=0&size=50
```
```bash
curl "http://localhost:8080/api/v1/orgs/my-org/access-report?page=0&size=50"
```
```json
{
  "content": [
    {
      "username": "alice",
      "profileUrl": "https://github.com/alice",
      "repositoryCount": 2,
      "repositoryAccess": [
        { "repository": "backend-api", "permission": "admin", "roleName": "admin", "repositoryUrl": "https://github.com/my-org/backend-api" },
        { "repository": "infra", "permission": "write", "roleName": "write", "repositoryUrl": "https://github.com/my-org/infra" }
      ]
    }
  ],
  "page": 0,
  "size": 50,
  "totalElements": 1240,
  "totalPages": 25
}
```

### Get the full, unpaginated report
```
GET /api/v1/orgs/{org}/access-report/full
```

### Get one user's access
```
GET /api/v1/orgs/{org}/access-report/users/{username}
```
```bash
curl "http://localhost:8080/api/v1/orgs/my-org/access-report/users/alice"
```

### Get everyone with access to one repository
```
GET /api/v1/orgs/{org}/access-report/repos/{repo}
```
```bash
curl "http://localhost:8080/api/v1/orgs/my-org/access-report/repos/backend-api"
```

### Force a refresh (bypass the cache)
```
POST /api/v1/orgs/{org}/access-report/refresh
```
```bash
curl -X POST "http://localhost:8080/api/v1/orgs/my-org/access-report/refresh"
```

### Errors
Failures (bad org name, GitHub rate limit exhausted, not found, etc.) return a
consistent JSON body:
```json
{
  "timestamp": "2026-07-12T10:15:30Z",
  "status": 404,
  "error": "Not Found",
  "message": "User 'bob' has no recorded access in org 'my-org'",
  "path": "/api/v1/orgs/my-org/access-report/users/bob"
}
```

---

## 4. Assumptions and design decisions

- **Data source**: GitHub's REST v3 API doesn't expose a single "org access matrix"
  endpoint, so the report is built from `GET /orgs/{org}/repos` (list repositories) plus
  one paginated `GET /repos/{owner}/{repo}/collaborators?affiliation=all` call per
  repository. `affiliation=all` includes direct collaborators, outside collaborators,
  and users who have access via **team** membership — the intent is a complete "who can
  access what" picture, not just directly-added collaborators.
- **Efficient API usage at scale**: repositories are listed once, then collaborators for
  all repositories are fetched **concurrently** (bounded by `github.max-concurrent-requests`,
  default 10) using a reactive `Flux.flatMap(..., concurrency)`, instead of looping over
  repos sequentially. For a 100-repo org this turns ~100 sequential round-trips into
  ~10 batches of parallel round-trips. Within a single repository's collaborator list,
  pages are still fetched in order (page 2 depends on knowing page 1 didn't already
  finish the list), but that pagination is cheap since most repos have far fewer than
  100 collaborators (one page).
- **Rate limits**: GitHub responses are inspected for `403`/`429` (rate limited) and
  `5xx` (transient) errors, which trigger an exponential backoff retry (3 attempts, up
  to 30s). A single repo that fails permanently (e.g. renamed/inaccessible) is logged
  and skipped rather than failing the entire report.
- **Permission model**: GitHub returns a set of boolean flags per collaborator (`pull`,
  `triage`, `push`, `maintain`, `admin`). These are collapsed to a single highest
  permission level (`admin` > `maintain` > `write` > `triage` > `read`) for a readable
  report, while `roleName` (GitHub's own label, including custom repo roles) is kept
  alongside it for full fidelity.
- **Archived repositories** are excluded by default, since they're typically read-only
  and not part of an active access review — this is a judgment call and easy to change
  in `GitHubClient.listOrganizationRepos`.
- **Caching**: a generated report is cached in-memory per organization for 15 minutes
  (configurable) using Caffeine, since building it requires an API call per repository.
  The `/refresh` endpoint forces an immediate rebuild. This is an in-memory cache, so it
  resets on restart and isn't shared across multiple instances — fine for a single
  instance; a real multi-instance deployment would move this to Redis or similar.
  Pagination (`/access-report?page=&size=`) exists mainly to keep individual HTTP
  response payloads small for orgs with 1000+ users, not to reduce GitHub API calls
  (those already happen once per cache TTL window regardless of how the result is paged
  out to clients).
- **Scope**: the service is read-only against GitHub — it never writes to a repo or
  changes anyone's access.

## 5. Project structure

```
src/main/java/com/example/githubaccessreport/
├── client/           GitHubClient — pagination, retries, rate-limit handling
├── config/           GitHubProperties, WebClient bean, Caffeine cache bean
├── controller/        REST endpoints
├── exception/         Custom exceptions + a global @RestControllerAdvice
├── model/github/       DTOs matching GitHub's JSON responses
├── model/report/       DTOs for this service's own API response shape
└── service/            AccessReportService — fetch, aggregate, cache
```

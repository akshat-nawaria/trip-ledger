# Technology Choice

**Language: Java 21. Framework: Spring Boot 3.** This is the stack the case study prefers. The reasons below explain why it also fits the problem.

| Need in this case study | Why Java 21 + Spring Boot fits |
|---|---|
| Exact, repeatable billing maths | `long` millimetres, `BigDecimal` money, and `StrictMath` give bit-identical results on every machine |
| Clean OOP (encapsulation, inheritance, polymorphism) | Interfaces, abstract classes and `record` value objects keep the rule engine small and extensible |
| Core logic testable without a web server or database | The `core` Maven module has **no Spring dependency at all**; a test fails the build if one sneaks in |
| Controller / service / repository layering | Spring Web + Spring Data JPA are the standard way to build it |
| Circuit breaker, retries, rate limits | Resilience4j, used as a plain library (no annotations), so the breaker is unit-tested without starting Spring |
| Route caching | Caffeine: in-process, one load per key even under concurrency, hit-rate stats built in |
| Metrics and structured logs | Spring Actuator + Micrometer → Prometheus → Grafana |
| Auth, roles, tenant isolation | Spring Security (JWT resource server) + PostgreSQL row-level security |
| Real-database tests | Testcontainers starts a real PostgreSQL inside the test run |
| One-command run | Docker Compose starts the app, database and tools |

## Versions

| Tool | Version |
|---|---|
| Java (JDK) | 21 (LTS) |
| Spring Boot | 3.5.x |
| Build | Maven 3.9+ (bundled with IntelliJ IDEA; pre-installed on GitHub Actions) |
| Database | PostgreSQL 16 |
| Container runtime | Docker + Docker Compose |

## Modules

| Module | Contains | Depends on Spring? |
|---|---|---|
| `core` | Domain, cleaning filters, anchors, segment rules, distance engine | **No** |
| `app` | REST API, security, persistence, routing adapters, jobs | Yes |

More modules (`bench` in S9) are added in the segments that need them. The mock routing provider lives inside `app` behind the `RoutingProvider` interface, so it didn't need its own module.

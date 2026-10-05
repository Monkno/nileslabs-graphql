# Niles Labs GraphQL with Gatling 3.16

A free local demo of native GraphQL performance testing with Kotlin. Inspired by [Gatling's October newsletter](https://www.linkedin.com/pulse/graphql-316-black-friday-prep-llm-benchmarking-akamas-building-z2ute/): GraphQL support, the smoke/load/spike/stress/soak sequence, and performance checks in CI.

Kotlin provides access to native GraphQL support in Gatling 3.16. See the [3.16 release notes](https://docs.gatling.io/release-notes/gatling/whats-new/3.16/).

## Run the demo

Install JDK 21+ and PowerShell 7. The Maven wrapper downloads Maven 3.9.16 and pinned dependencies from Maven Central. Set `JAVA_HOME` to your JDK.

```powershell
./mvnw.cmd verify
./scripts/run-demo.ps1 -Quick
./scripts/run-demo.ps1
./scripts/run-demo.ps1 -OnlyOverload
```

On Linux/macOS use `./mvnw verify`, then `pwsh -File scripts/run-demo.ps1`. The full demo runs the five workload profiles in order, a native feature demo, four negative controls, and a controlled overload comparison. The quick demo omits the workload sequence and overload comparison. Everything runs against temporary servers bound to loopback. GitHub Actions runs the quick demo and retains reports.

Reports: `target/gatling/<run>/index.html`. Measured summaries and command logs: `artifacts/demo/`. Expected negative runs have KO requests and failing Gatling assertions; the wrapper accepts them only when a new report contains that evidence.

## What Gatling tests

The main journey uses named `.graphql` files, native variables, `graphqlData` checks, `postCheck` and `failOnErrors()`. It selects one of two users in the Gatling Session, validates that returned IDs match, and checks nested post/comment data and task ownership. Gatling groups results by operation name automatically.

| Operation | Request | Checks |
| --- | --- | --- |
| UserProfile | Query by ID | Correct user ID and a nonempty name |
| PostsWithComments | Two posts, two comments per post | Authors and nested comment data |
| UserTodos | Two tasks for the selected user | Correct owner and boolean completion |

Each operation checks HTTP 200. The native error policy rejects GraphQL errors even with HTTP 200 and partial data. Other negative fixtures return null data, malformed JSON or HTTP 500.

The separate native feature simulation exercises GET, a local mutation with a read-back check, `extensions`, and Automatic Persisted Queries. It verifies actual GET requests and APQ cache misses/hits. Mutations affect only the local fixture.

## The free workload sequence

The [native GraphQL Community limits](https://docs.gatling.io/reference/script/graphql/setup/) are five users and five minutes per test. These profiles create at most five total users, then repeat their journeys. Each journey pauses one second between operations. Arrival injection and per-user journey duration are separate.

| Stage | Total users | Arrival pattern | Journey loop duration per user |
| --- | --- | --- | --- |
| Smoke | 1 | Immediate | One journey |
| Load | 2 | Ramp over 5 s | 20 s |
| Spike | 5 | Immediate burst | 15 s |
| Stress | 5 | Ramp over 20 s | 40 s |
| Soak | 2 | Ramp over 5 s | 120 s |

The soak is deliberately short. It demonstrates sustained execution within the free limit; it does not establish long-term stability. The stress profile demonstrates increasing pressure within a five-user budget; it does not locate a production breaking point.

Assertions require zero failures, at least one completed journey per user, per-operation p95 <= 2000 ms and global p99 <= 4000 ms. Latency budgets are starter thresholds, not Niles service guarantees. Every request times out after ten seconds; every run has a hard duration cap below five minutes.

The congested fixture adds 500 ms of service time and returns HTTP 200 with GraphQL errors when more than two requests overlap. The two-user load should pass; the five-user spike should fail. This is an injected capacity rule for demonstrating Gatling's detection and assertions, not a benchmark of the public API.

The loop uses Gatling's default immediate exit at its duration boundary, so the last iteration can stop part-way through a journey. Request totals can therefore be nonmultiples of three. Each operation has its own minimum-count assertion to detect skipped or unbuilt requests.

## Public Niles smoke

```powershell
./mvnw.cmd gatling:test -Dtarget=public -Dprofile=smoke
```

This sends three read-only POST operations to `https://playground.nileslabs.com/api/v1/graphql`. Public load, spike, stress, soak, GET/APQ and fault modes are rejected. The main documents were checked against Niles' live schema. One smoke has too few samples for a performance conclusion.

Local commands can customize a bounded profile:

```powershell
./mvnw.cmd gatling:test -Dprofile=stress -DstressApproved=true
./mvnw.cmd gatling:test -Dprofile=smoke -Dtransport=get
./mvnw.cmd gatling:test -Dprofile=smoke -Dtransport=apq
./mvnw.cmd gatling:test -Dprofile=load -Dp95Ms=1000 -Dp99Ms=2000
```

Unknown profiles, targets, transports, fault modes and invalid latency budgets fail before the fixture starts. The fixture uses GraphQL Java to execute documents, variables and field selection; it is a small subset of the Niles schema.

## Evidence and scope

- [Article coverage and free limitations](docs/article-coverage.md)
- [Working without Cloud credits](docs/zero-credits.md)
- [Recorded verification](docs/verification.md)

- [Niles GraphQL documentation](https://playground.nileslabs.com/docs/graphql/queries)

Distributed generators, Enterprise dashboards and AI report analysis are not part of this zero-credit run. This GraphQL endpoint also cannot demonstrate LLM token-streaming benchmarks. Local reports measure the fixture and generator on the same machine.

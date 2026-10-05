# Recorded verification

Executed on October 5, 2026 with Gatling 3.16.0 and native GraphQL 3.16.0.1, Maven 3.9.16 and GraalVM JDK 25 on Windows. All workload stages used the local GraphQL Java fixture. Measured summaries are in [local-results.json](local-results.json).

| Run | Requests | KO | p95 (ms) | p99 (ms) |
| --- | ---: | ---: | ---: | ---: |
| congested-load | 34 | 0 | 514 | 751 |
| congested-spike | 65 | 39 | 785 | 785 |
| load | 58 | 0 | 6 | 239 |
| native-features | 7 | 0 | 189 | 189 |
| negative-graphql-error | 3 | 3 | 278 | 278 |
| negative-http-error | 3 | 3 | 259 | 259 |
| negative-invalid-json | 3 | 3 | 238 | 238 |
| negative-null-data | 3 | 3 | 263 | 263 |
| smoke | 3 | 0 | 301 | 301 |
| soak | 357 | 0 | 4 | 6 |
| spike | 110 | 0 | 16 | 284 |
| stress | 295 | 0 | 5 | 6 |

The five healthy stages passed their assertions. Each negative control produced three KO requests and a failing zero-errors assertion for the intended cause. The congested fixture passed its two-user load, then the five-user spike produced 39 KO requests out of 65. Its peak in-flight request counts were two and five respectively. The overload is an injected rule, not a Niles capacity measurement.

The native feature demo made seven HTTP requests: four GET requests, three APQ misses and one cache hit. The mutation's state was verified by a subsequent query. APQ retries are included in the reported HTTP count.

`mvnw verify` passed four JUnit test methods covering configuration constraints, free limits and document/variable execution.

A separate public smoke sent exactly three read-only POST queries to Niles and passed with zero KO. Per-operation p95 was 1459 ms for UserProfile, 424 ms for PostsWithComments and 417 ms for UserTodos. Three samples cannot establish performance or capacity; network and cold-start effects are included.

The default loop stops at its duration boundary, so the last journey may be incomplete. The tests assert minimum counts for every operation. Local load results include cold JVM startup and share a machine with the generator; compare repeat runs before interpreting small latency differences.

Raw HTML reports and logs remain in ignored `target/gatling/` and `artifacts/` folders. CI exports reports with a three-day retention policy. No Enterprise run, subscription or billing change was made.

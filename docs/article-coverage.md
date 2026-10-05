# Article coverage

Reference: [Gatling newsletter, October 5, 2026](https://www.linkedin.com/pulse/graphql-316-black-friday-prep-llm-benchmarking-akamas-building-z2ute/).

| Article topic | Repository demonstration | Boundary |
| --- | --- | --- |
| Native GraphQL in 3.16 | Kotlin SDK, named queries, variables and nested checks | TypeScript native support is still forthcoming |
| HTTP 200 with GraphQL failures | Native failOnErrors plus an injected partial-data response | Negative controls must produce a real Gatling KO/report |
| Operation-level reporting | Native names rather than a single /graphql metric | Global and per-operation latency assertions |
| Mutations and GET | Local mutation/read-back and GET queries | Public smoke is read-only POST |
| Automatic Persisted Queries | Real hash lookup, registration and subsequent cache hit | Fixture implementation, not Niles APQ support |
| Black Friday preparation | Smoke, load, spike, stress and short soak in order | Five total users, less than five minutes per local test |
| Performance culture with AI | Versioned tests, failing CI gates and retained evidence | No claim that generated tests prove production readiness |
| LLM benchmarking with Akamas | Documented as a separate workload | Needs an inference server, prompts, streamed tokens, TTFT and inter-token latency |
| Scaling generators to AWS | Documented as outside this demo | Distributed infrastructure can incur charges |

Native subscriptions are a separate GraphQL capability. The initial fixture supports HTTP queries and mutations; it does not implement graphql-transport-ws. The repository makes no subscription coverage claim.

The native component's [Community limits](https://docs.gatling.io/reference/script/graphql/setup/) differ from generic HTTP testing. Local native GraphQL tests stop after five users or five minutes. The chosen profiles stay under both limits, including their drain allowance. They teach the workflow and demonstrate error detection at a small scale.

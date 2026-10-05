# More experiments, one step at a time

Start with the [README smoke test](../README.md#2-run-your-first-test). Commands below use Windows PowerShell. On macOS/Linux, replace `.\mvnw.cmd` with `./mvnw` and run `chmod +x mvnw` once.

## Run the five workload profiles

Each simulated user follows the same three-query journey, with a one-second pause between operations. Longer profiles repeat it.

| Profile | Total users | How they start | Journey time per user |
| --- | ---: | --- | --- |
| smoke | 1 | Immediately | One journey |
| load | 2 | Over 5 seconds | 20 seconds |
| spike | 5 | Together | 15 seconds |
| stress | 5 | Over 20 seconds | 40 seconds |
| soak | 2 | Over 5 seconds | 120 seconds |

Run them individually, or use the helper below for the complete sequence:

```powershell
.\mvnw.cmd gatling:test -Dprofile=smoke
.\mvnw.cmd gatling:test -Dprofile=load
.\mvnw.cmd gatling:test -Dprofile=spike
.\mvnw.cmd gatling:test -Dprofile=stress -DstressApproved=true
.\mvnw.cmd gatling:test -Dprofile=soak
```

The stress flag acknowledges that you are selecting the ramped profile. It still runs locally and creates at most five users. This short soak demonstrates sustained execution; it cannot establish long-term stability.

The loop can stop during the last journey when its duration expires, so totals need not be multiples of three. Gatling requires a minimum request count for every operation.

## Understand the pass/fail rules

The main simulation requires zero failed requests, enough samples for each operation, per-operation p95 at most 2000 ms and global p99 at most 4000 ms. These are example thresholds you can adapt to your own requirements.

To try tighter latency checks:

```powershell
.\mvnw.cmd gatling:test -Dprofile=load -Dp95Ms=1000 -Dp99Ms=2000
```

`p95Ms` and `p99Ms` accept integers from 1 to 60000; p99 must be at least p95. Requests time out after ten seconds. Each profile has a hard run cap below five minutes.

The congested local server adds 500 ms of processing time. If more than two requests overlap, it returns HTTP 200 with a deliberate GraphQL error. Use the comparison helper to see a passing load and failing spike; that rule illustrates error detection, rather than the capacity of the public API.

## Explore native GraphQL features

Try the main read-only journey using GET or Automatic Persisted Queries (APQ) against the local server:

```powershell
.\mvnw.cmd gatling:test -Dtransport=get
.\mvnw.cmd gatling:test -Dtransport=apq
```

APQ lets a client identify a query by its hash. If the server has not seen it, the client sends the full query to register it. The fixture implements that exchange and tracks cache hits.

A separate one-user simulation demonstrates APQ over GET, a local task mutation, a subsequent query that checks the changed value, and response `extensions`:

```powershell
.\mvnw.cmd gatling:test "-Dgatling.simulationClass=performance.NativeFeaturesSimulation"
```

It checks actual GET requests and APQ misses/hits, rather than just successful responses. APQ negotiation retries count as HTTP requests in the report. This simulation runs locally; it does not assume that Niles supports APQ or accepts these mutations.

## Run the helper

Install PowerShell 7 for these commands. Each uses local servers and writes summaries and logs to `artifacts/demo/`, with HTML reports under `target/gatling/`.

```powershell
# Smoke, native features and four deliberate error cases
pwsh -File scripts/run-demo.ps1 -Quick

# Passing load followed by a deliberately failing spike
pwsh -File scripts/run-demo.ps1 -OnlyOverload

# All five profiles, native features, error cases and overload comparison
pwsh -File scripts/run-demo.ps1
```

Run one command at a time. The full demo takes several minutes. Its negative cases return GraphQL errors, null data, invalid JSON and HTTP 500. Those Gatling runs must fail with KO requests and the expected error. The helper verifies their reports and treats those demonstrated failures as successful checks of the detector. A crash or an unrelated failure still fails the helper.

## See the same checks in CI

Pushes and pull requests run compilation, JUnit checks and the quick local demo in GitHub Actions. You can also run the full demo:

1. Open the repository's **Actions** tab.
2. Choose **Full free GraphQL demo**.
3. Select **Run workflow**, choose your branch and start it.
4. Open the completed run and download `full-gatling-local-evidence` from **Artifacts**.
5. Extract it and open a Gatling report's `index.html`.

Reports are retained for three days. Standard GitHub-hosted execution is free for this public repository; artifact storage follows the account allowance. These workflows never start a Gatling Enterprise run. [More about free execution](zero-credits.md).

## Change a query

1. Open `src/test/resources/graphql/UserProfile.graphql` to see a named query and its `$id` variable.
2. Open `src/test/kotlin/performance/GraphQLSimulation.kt` to see how Gatling supplies the ID and checks the response.
3. Change a field or a check supported by the local schema in `GraphQLFixture.kt`.
4. Run `.\mvnw.cmd verify` to compile and run JUnit checks, then `.\mvnw.cmd gatling:test` for the smoke.
5. Read the operation's report before adding more traffic.

`verify` alone does not run a Gatling workload. When adapting this project to another service, update its schema, endpoint, variables and checks together.

## Troubleshooting

| What you see | What to do |
| --- | --- |
| Java or JAVA_HOME error | Install JDK 21+, set JAVA_HOME to its installation folder and reopen your terminal. Check `java -version`. |
| First run is downloading files | Let the Maven wrapper finish. Later runs reuse the dependency cache. |
| `Permission denied` on macOS/Linux | Run `chmod +x mvnw`, then retry `./mvnw gatling:test`. |
| `pwsh` is not found | Install PowerShell 7 for the helper, or run a single test with the Maven wrapper. |
| BUILD FAILURE with a deliberate fault mode | Read the KO errors and failed assertion. This is expected only for the documented negative experiments. |
| BUILD FAILURE on the healthy smoke | Check the report and terminal output. Do not assume this is an expected negative case. |
| Public load or an unknown setting is rejected | The public target is smoke-only. Use the fixture for load and consult the supported options above. |

The [recorded verification](verification.md) gives sample outcomes. Response times and request counts vary between machines and runs.

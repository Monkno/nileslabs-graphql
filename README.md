# Try GraphQL performance testing with Gatling

New to Gatling? Start here. This Kotlin example uses Gatling 3.16 to run GraphQL queries, check their responses and generate a report you can open in your browser.

Your first test runs on a small local GraphQL server that starts and stops automatically. You need no Gatling Cloud account or credits. After that, try more users and see Gatling catch a GraphQL failure even when the server returns HTTP 200.

Inspired by [Gatling's GraphQL 3.16 newsletter](https://www.linkedin.com/pulse/graphql-316-black-friday-prep-llm-benchmarking-akamas-building-z2ute/).

## 1. Get the project

Install Git and a JDK version 21 or later. Check that `java -version` works and that `JAVA_HOME` points to your JDK. The included Maven wrapper downloads Maven and dependencies on the first run; you do not need to install Maven separately.

```sh
git clone https://github.com/Monkno/nileslabs-graphql.git
cd nileslabs-graphql
```

## 2. Run your first test

On Windows, open PowerShell in the project folder:

```powershell
.\mvnw.cmd gatling:test
```

On macOS or Linux:

```sh
chmod +x mvnw
./mvnw gatling:test
```

This is a **smoke test**: one simulated user sends three queries. The first run can take longer while dependencies download. Gatling checks the response data and requires zero failed requests.

| Query | What it checks |
| --- | --- |
| UserProfile | The returned ID matches the requested user, and the name is not empty |
| PostsWithComments | Two posts with authors and two comments per post |
| UserTodos | Two tasks belonging to the selected user |

## 3. Open the report

At the end, Gatling prints the report location. Open its `index.html` in your browser. It is inside `target/gatling/<run>/`.

Start with these results:

- **OK / KO:** passed and failed requests. The local smoke should show 3 OK and 0 KO.
- **Operation names:** results for `query UserProfile`, `query PostsWithComments` and `query UserTodos`, even though they share one GraphQL endpoint.
- **Response times:** p95 means 95% of measured requests finished within that time. Three requests are enough to check the setup; use a longer profile to collect more samples.

If the build fails, read the failed assertion and the report's errors before changing a threshold. [Troubleshooting and more commands](docs/running-tests.md#troubleshooting).

## 4. Try a little more traffic

Run these one at a time in Windows PowerShell:

```powershell
.\mvnw.cmd gatling:test -Dprofile=load
.\mvnw.cmd gatling:test -Dprofile=spike
```

On macOS/Linux, replace `.\mvnw.cmd` with `./mvnw`.

`load` introduces two users gradually. `spike` starts five users together. Both repeat the same query journey against the local server. Compare their operation counts, response times and failures in the reports.

## 5. See Gatling detect a problem

A GraphQL response can contain an `errors` field while its HTTP status is still 200. This demo checks both.

```powershell
.\mvnw.cmd gatling:test -DfixtureMode=graphql-error
```

This command deliberately fails: expect 3 KO, a failed zero-errors assertion and Maven's `BUILD FAILURE`. The server is returning an injected GraphQL error, and Gatling is detecting it.

For a load-versus-overload comparison, install PowerShell 7 and run:

```powershell
pwsh -File scripts/run-demo.ps1 -OnlyOverload
```

The helper runs two tests against a server with a controlled capacity rule. Two users should pass; five users should trigger GraphQL errors. It verifies the expected failure and finishes successfully only when both outcomes match. [See the measured example](docs/verification.md).

## Try the public Niles endpoint

After the local smoke works, you can send three read-only queries to the [Niles GraphQL playground](https://playground.nileslabs.com/docs/graphql/queries):

```powershell
.\mvnw.cmd gatling:test -Dtarget=public -Dprofile=smoke
```

The public target accepts only this small POST smoke. All load profiles, mutations and deliberate errors run locally. The local server models a small subset of the Niles schema; its timings do not measure Niles capacity.

## Keep experimenting

The native GraphQL Community component allows [five users and five minutes per test](https://docs.gatling.io/reference/script/graphql/setup/). Every included profile stays within those limits. These small tests teach the workflow; production capacity planning needs a workload and scale appropriate to your service.

Kotlin supports native GraphQL in Gatling 3.16. [The release notes describe SDK availability](https://docs.gatling.io/release-notes/gatling/whats-new/3.16/).

- [More profiles, GET, mutations, APQ and CI: step-by-step guide](docs/running-tests.md)
- [What this demo covers from the article](docs/article-coverage.md)
- [What you can do without Cloud credits](docs/zero-credits.md)
- [Recorded results and their limits](docs/verification.md)

To start adapting the example, edit a named query in `src/test/resources/graphql/` and its response checks in `src/test/kotlin/performance/GraphQLSimulation.kt`. Keep the local smoke small while you learn how Gatling reports the result.
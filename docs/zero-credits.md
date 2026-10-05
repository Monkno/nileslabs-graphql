# Working without Cloud credits

Local Gatling execution, Kotlin compilation, JUnit checks and HTML reports do not use Enterprise Cloud generators. This repository requires no Cloud token.

An exhausted Enterprise trial can still expose existing tests and historical reports. Access to those pages does not establish that another Cloud run can start. A team's allocation also does not replace the organization's remaining balance.

[Gatling's credit rules](https://docs.gatling.io/reference/run-tests/credits/) specify one credit per load generator per minute, including initialization. Successful Enterprise building-from-sources and private deployment are not charged; failure can charge the equivalent of one execution minute. This repository invokes neither Enterprise operation.

The [native GraphQL Community component](https://docs.gatling.io/reference/script/graphql/setup/) has a separate five-user/five-minute limit. Staying local avoids Cloud credits, while still respecting that component limit.

You can compile, test contracts, run local profiles, verify negative cases and generate HTML reports without Cloud credits. A manual bounded public smoke is available. Local reports do not automatically appear in the Enterprise dashboard. Distributed Enterprise runs need available credits and an eligible plan.

This repository is public and uses standard Ubuntu GitHub-hosted runners, whose execution minutes are [free for public repositories](https://docs.github.com/en/billing/concepts/product-billing/github-actions). Artifact storage follows the account allowance; these workflows compress reports and retain them for three days. Local runs do not need hosted CI or artifact storage.

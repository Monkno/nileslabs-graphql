package performance

import io.gatling.javaapi.core.CoreDsl.*
import io.gatling.javaapi.core.Simulation
import io.gatling.javaapi.graphql.GraphQlDsl.*
import io.gatling.javaapi.http.HttpDsl.*
import java.time.Duration

class GraphQLSimulation : Simulation() {
    private var fixture: GraphQLFixture? = null
    init {
        val config = RunConfig.fromSystem()
        fixture = if (config.target == "fixture") GraphQLFixture(config.fixtureMode) else null
        val baseUrl = fixture?.baseUrl ?: "https://playground.nileslabs.com"
        val httpProtocol = http.baseUrl(baseUrl).disableWarmUp()
            .userAgentHeader("NilesGraphQL-Gatling/1.0 (bounded demonstration)")
        var graphProtocol = graphql.endpoint("/api/v1/graphql").failOnErrors().requireNamedOperations()
        graphProtocol = when (config.transport) {
            "get" -> graphProtocol.queriesOverGet()
            "apq" -> graphProtocol.automaticPersistedQueries()
            else -> graphProtocol
        }
        val journey = exec(setInSession("#{randomOneOf(1, 2)}", "userId"))
            .exec(
                graphql.file("graphql/UserProfile.graphql").variable("id", "#{userId}")
                    .requestTimeout(Duration.ofSeconds(10))
                    .check(status().`is`(200), graphqlData.jsonPath("$.user.id").`is` { session -> session.getString("userId") },
                        graphqlData.jsonPath("$.user.name").saveAs("userName"))
                    .postCheck { session ->
                        require(!session.getString("userName").isNullOrBlank()) { "Empty user name" }
                        session
                    },
                pause(1),
                graphql.file("graphql/PostsWithComments.graphql").variables(mapOf("limit" to 2, "commentLimit" to 2))
                    .requestTimeout(Duration.ofSeconds(10))
                    .check(status().`is`(200), graphqlData.jsonPath("$.posts[*].id").count().`is`(2),
                        graphqlData.jsonPath("$.posts[*].user.name").count().`is`(2),
                        graphqlData.jsonPath("$.posts[0].comments[*].body").count().`is`(2),
                        graphqlData.jsonPath("$.posts[1].comments[*].body").count().`is`(2),
                        graphqlData.jsonPath("$.posts[*].comments[*].body").count().`is`(4)),
                pause(1),
                graphql.file("graphql/UserTodos.graphql").variables(mapOf("userId" to "#{userId}", "limit" to 2))
                    .requestTimeout(Duration.ofSeconds(10))
                    .check(status().`is`(200), graphqlData.jsonPath("$.todos[*].id").count().`is`(2),
                        graphqlData.jsonPath("$.todos[*].user.id").findAll().saveAs("todoOwners"),
                        graphqlData.jsonPath("$.todos[*].completed").ofBoolean().count().`is`(2))
                    .postCheck { session ->
                        require(session.getList<String>("todoOwners").all { it == session.getString("userId") }) { "Wrong todo owner" }
                        session
                    }
            )
        val users = scenario("GraphQL ${config.profile.name.lowercase()}")
        val workload = if (config.profile == Profile.SMOKE) users.exec(journey)
            else users.during(Duration.ofSeconds(config.profile.seconds)).on(journey)
        val injection = if (config.profile.rampSeconds == 0L) atOnceUsers(config.profile.users)
            else rampUsers(config.profile.users).during(Duration.ofSeconds(config.profile.rampSeconds))
        setUp(workload.injectOpen(injection))
            .protocols(httpProtocol, graphProtocol)
            .maxDuration(Duration.ofSeconds(config.profile.seconds + config.profile.rampSeconds + 35))
            .assertions(
                global().failedRequests().count().`is`(0L),
                global().allRequests().count().gte((config.profile.users * 3).toLong()),
                details("query UserProfile").allRequests().count().gte(config.profile.users.toLong()),
                details("query PostsWithComments").allRequests().count().gte(config.profile.users.toLong()),
                details("query UserTodos").allRequests().count().gte(config.profile.users.toLong()),
                global().responseTime().percentile4().lte(config.p99Ms),
                forAll().responseTime().percentile3().lte(config.p95Ms)
            )
    }
    override fun after() { fixture?.close() }
}

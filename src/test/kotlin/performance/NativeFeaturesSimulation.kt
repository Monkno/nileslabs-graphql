package performance

import io.gatling.javaapi.core.CoreDsl.*
import io.gatling.javaapi.core.Simulation
import io.gatling.javaapi.graphql.GraphQlDsl.*
import io.gatling.javaapi.http.HttpDsl.*
import java.time.Duration

class NativeFeaturesSimulation : Simulation() {
    private val fixture: GraphQLFixture
    init {
        require(System.getProperty("target", "fixture") == "fixture") { "Native feature demo is local-only" }
        fixture = GraphQLFixture()
        val protocol = graphql.endpoint("/api/v1/graphql").failOnErrors().requireNamedOperations().automaticPersistedQueriesOverGet()
        val query = graphql.file("graphql/UserProfile.graphql").variable("id", "1")
            .requestTimeout(Duration.ofSeconds(10))
            .check(status().`is`(200), graphqlData.jsonPath("$.user.id").`is`("1"),
                graphqlExtensions.jsonPath("$.fixture").`is`("local"))
        val journey = scenario("Native queries, GET, APQ and mutation").exec(
            query, query,
            graphql.file("graphql/UpdateTodo.graphql").variables(mapOf("id" to "1-1", "completed" to true))
                .requestTimeout(Duration.ofSeconds(10))
                .check(status().`is`(200), graphqlData.jsonPath("$.updateTodo.completed").ofBoolean().`is`(true)),
            graphql.file("graphql/UserTodos.graphql").variables(mapOf("userId" to "1", "limit" to 2))
                .requestTimeout(Duration.ofSeconds(10))
                .check(graphqlData.jsonPath("$.todos[0].completed").ofBoolean().`is`(true))
        )
        setUp(journey.injectOpen(atOnceUsers(1)))
            .protocols(http.baseUrl(fixture.baseUrl).disableWarmUp(), protocol)
            .maxDuration(Duration.ofSeconds(45))
            .assertions(global().failedRequests().count().`is`(0L), global().successfulRequests().count().gte(4L))
    }
    override fun after() {
        try {
            check(fixture.getRequests.get() > 0) { "GET transport was not exercised" }
            check(fixture.apqMisses.get() > 0 && fixture.apqHits.get() > 0) { "APQ negotiation/cache was not exercised" }
        } finally { fixture.close() }
    }
}

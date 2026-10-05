package performance

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RunConfigTest {
    @Test fun `all free profiles stay within five total users and five minutes`() {
        Profile.entries.forEach {
            assertTrue(it.users <= 5)
            assertTrue(it.seconds + it.rampSeconds + 35 < 300)
            assertEquals(it, RunConfig.parse(mapOf("profile" to it.name.lowercase(), "stressApproved" to "true")).profile)
        }
    }
    @Test fun `public load and faults are rejected`() {
        Profile.entries.filter { it != Profile.SMOKE }.forEach {
            assertThrows(IllegalArgumentException::class.java) { RunConfig.parse(mapOf("target" to "public", "profile" to it.name)) }
        }
        assertThrows(IllegalArgumentException::class.java) { RunConfig.parse(mapOf("target" to "public", "fixtureMode" to "graphql-error")) }
    }
    @Test fun `invalid parameters fail closed`() {
        listOf("profile" to "unknown", "target" to "https://example.com", "fixtureMode" to "unknown", "transport" to "unknown", "p95Ms" to "NaN", "p95Ms" to "0", "p95Ms" to "60001", "p95Ms" to "-1", "p95Ms" to "1.5", "p95Ms" to " 1000 ").forEach { (key, value) ->
            assertThrows(IllegalArgumentException::class.java) { RunConfig.parse(mapOf(key to value)) }
        }
        assertThrows(IllegalArgumentException::class.java) { RunConfig.parse(mapOf("profile" to "stress")) }
        assertThrows(IllegalArgumentException::class.java) { RunConfig.parse(mapOf("p95Ms" to "3000", "p99Ms" to "2000")) }
    }
    @Test fun `fixture executes named documents with variables and rejects schema errors`() {
        GraphQLFixture().use { fixture ->
            val variables = mapOf<String, Any>("id" to "1", "userId" to "1", "limit" to 2, "commentLimit" to 2)
            listOf("UserProfile", "PostsWithComments", "UserTodos").forEach { name ->
                val query = javaClass.getResource("/graphql/$name.graphql")!!.readText()
                val response = fixture.execute(query, name, variables)
                assertFalse(response.containsKey("errors"), response.toString())
                assertNotNull(response["data"])
                assertTrue(fixture.execute(query, name, emptyMap()).containsKey("errors"))
            }
            assertTrue(fixture.execute("query Bad { missingField }", "Bad", emptyMap()).containsKey("errors"))
        }
    }
}

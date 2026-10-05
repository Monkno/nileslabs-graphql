package performance

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpServer
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class GraphQLFixture(private val mode: String = "healthy") : AutoCloseable {
    private val mapper = ObjectMapper()
    private val executor = Executors.newVirtualThreadPerTaskExecutor()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val active = AtomicInteger()
    val requestCount = AtomicInteger()
    val peakRequests = AtomicInteger()
    val apqMisses = AtomicInteger()
    val apqHits = AtomicInteger()
    val getRequests = AtomicInteger()
    private val persisted = ConcurrentHashMap<String, String>()
    private val completed = ConcurrentHashMap<String, Boolean>()
    private val users = listOf(mapOf("id" to "1", "name" to "Fixture Alice"), mapOf("id" to "2", "name" to "Fixture Bob"))
    private fun todos(userId: String, limit: Int) = (1..2).take(limit).map { id ->
        mapOf("id" to "$userId-$id", "title" to "Fixture todo $id", "completed" to (completed["$userId-$id"] ?: (id == 2)), "user" to users.first { it["id"] == userId })
    }
    private val schema = """
        type User { id: ID!, name: String! }
        type Comment { id: ID!, body: String! }
        type Post { id: ID!, title: String!, user: User!, comments(_limit: Int!): [Comment!]! }
        type Todo { id: ID!, title: String!, completed: Boolean!, user: User! }
        type Query { user(id: ID!): User, posts(_limit: Int!): [Post!]!, todos(user_id: ID!, _limit: Int!): [Todo!]! }
        type Mutation { updateTodo(id: ID!, completed: Boolean!): Todo! }
    """.trimIndent()
    private val wiring = RuntimeWiring.newRuntimeWiring()
        .type("Query") { type -> type
            .dataFetcher("user") { env -> users.find { it["id"] == env.getArgument<String>("id") } }
            .dataFetcher("posts") { env -> (1..2).take(env.getArgument<Int>("_limit")!!).map { id ->
                mapOf("id" to id.toString(), "title" to "Fixture post $id", "user" to users[id - 1])
            } }
            .dataFetcher("todos") { env -> todos(env.getArgument<String>("user_id")!!, env.getArgument<Int>("_limit")!!) }
        }
        .type("Post") { type -> type.dataFetcher("comments") { env ->
            (1..2).take(env.getArgument<Int>("_limit")!!).map { id -> mapOf("id" to id.toString(), "body" to "Fixture comment $id") }
        } }
        .type("Mutation") { type -> type.dataFetcher("updateTodo") { env ->
            val id = env.getArgument<String>("id")!!
            completed[id] = env.getArgument<Boolean>("completed")!!
            todos(id.substringBefore("-"), 2).first { it["id"] == id }
        } }.build()
    private val engine = GraphQL.newGraphQL(SchemaGenerator().makeExecutableSchema(SchemaParser().parse(schema), wiring)).build()
    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}"

    fun execute(query: String, operationName: String?, variables: Map<String, Any>): Map<String, Any> =
        engine.execute(ExecutionInput.newExecutionInput().query(query).operationName(operationName).variables(variables).build()).toSpecification()

    init {
        server.executor = executor
        server.createContext("/api/v1/graphql") { exchange ->
            val concurrency = active.incrementAndGet()
            requestCount.incrementAndGet()
            peakRequests.accumulateAndGet(concurrency, ::maxOf)
            try {
                require(exchange.requestMethod in setOf("POST", "GET"))
                val node = if (exchange.requestMethod == "GET") {
                    getRequests.incrementAndGet()
                    val values = exchange.requestURI.rawQuery.orEmpty().split("&").filter { it.contains("=") }.associate {
                        val parts = it.split("=", limit = 2)
                        URLDecoder.decode(parts[0], StandardCharsets.UTF_8) to URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
                    }
                    mapper.createObjectNode().apply {
                        values.forEach { (key, value) -> if (key in setOf("variables", "extensions")) set(key, mapper.readTree(value)) else put(key, value) }
                    }
                } else mapper.readTree(exchange.requestBody.readNBytes(16385).also { require(it.size <= 16384) })
                var query = node.path("query").asText("")
                val hash = node.path("extensions").path("persistedQuery").path("sha256Hash").asText("")
                var result: Map<String, Any>
                if (hash.isNotEmpty() && query.isEmpty() && !persisted.containsKey(hash)) {
                    apqMisses.incrementAndGet()
                    result = mapOf("errors" to listOf(mapOf("message" to "PersistedQueryNotFound", "extensions" to mapOf("code" to "PERSISTED_QUERY_NOT_FOUND"))))
                } else {
                    if (hash.isNotEmpty()) {
                        if (query.isEmpty()) { query = persisted.getValue(hash); apqHits.incrementAndGet() }
                        else {
                            val actualHash = MessageDigest.getInstance("SHA-256").digest(query.toByteArray()).joinToString("") { "%02x".format(it) }
                            require(actualHash == hash) { "Persisted query hash mismatch" }
                            persisted[hash] = query
                        }
                    }
                    @Suppress("UNCHECKED_CAST")
                    val variables = if (node.has("variables")) mapper.convertValue(node.get("variables"), Map::class.java) as Map<String, Any> else emptyMap()
                    result = execute(query, node.path("operationName").takeUnless { it.isMissingNode }?.asText(), variables)
                    result = result + ("extensions" to mapOf("fixture" to "local"))
                    if (mode == "congested") Thread.sleep(500)
                    if (mode == "graphql-error" || (mode == "congested" && concurrency > 2)) {
                        result = result + ("errors" to listOf(mapOf("message" to "Deliberate fixture GraphQL failure")))
                    }
                    if (mode == "null-data") result = mapOf("data" to mapper.nullNode())
                }
                val bytes = if (mode == "invalid-json") "not json".toByteArray() else mapper.writeValueAsBytes(result)
                exchange.responseHeaders.set("Content-Type", "application/json")
                exchange.sendResponseHeaders(if (mode == "http-error") 500 else 200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            } catch (error: Exception) {
                val bytes = mapper.writeValueAsBytes(mapOf("errors" to listOf(mapOf("message" to (error.message ?: "Invalid request")))))
                exchange.sendResponseHeaders(400, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            } finally { active.decrementAndGet(); exchange.close() }
        }
        server.start()
    }
    override fun close() {
        println("Fixture: ${requestCount.get()} HTTP requests, peak concurrency ${peakRequests.get()}, GET ${getRequests.get()}, APQ misses ${apqMisses.get()}, hits ${apqHits.get()}")
        server.stop(0)
        executor.close()
    }
}

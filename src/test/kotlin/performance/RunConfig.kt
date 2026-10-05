package performance

enum class Profile(val seconds: Long, val users: Int, val rampSeconds: Long) {
    SMOKE(0, 1, 0), LOAD(20, 2, 5), SPIKE(15, 5, 0), STRESS(40, 5, 20), SOAK(120, 2, 5)
}

data class RunConfig(
    val profile: Profile,
    val target: String,
    val fixtureMode: String,
    val p95Ms: Int,
    val p99Ms: Int,
    val transport: String
) {
    companion object {
        fun parse(properties: Map<String, String>): RunConfig {
            val profile = Profile.valueOf((properties["profile"] ?: "smoke").uppercase())
            val target = properties["target"] ?: "fixture"
            val mode = properties["fixtureMode"] ?: "healthy"
            require(target in setOf("fixture", "public")) { "Only fixture or public targets are allowed" }
            require(target != "public" || profile == Profile.SMOKE) { "Public target is smoke-only" }
            require(mode in setOf("healthy", "graphql-error", "congested", "null-data", "invalid-json", "http-error")) { "Unknown fixtureMode" }
            require(target != "public" || mode == "healthy") { "Faults are local-only" }
            require(profile != Profile.STRESS || properties["stressApproved"] == "true") { "Stress requires stressApproved=true" }
            val transport = properties["transport"] ?: "post"
            require(transport in setOf("post", "get", "apq")) { "Unknown transport" }
            require(target != "public" || transport == "post") { "Public smoke uses POST only" }
            fun threshold(key: String, fallback: Int): Int {
                val raw = properties[key] ?: fallback.toString()
                require(raw.matches(Regex("[0-9]+"))) { "$key must be an integer" }
                return raw.toInt().also { require(it in 1..60000) { "$key outside 1..60000" } }
            }
            val p95 = threshold("p95Ms", 2000)
            val p99 = threshold("p99Ms", 4000)
            require(p99 >= p95) { "p99Ms must be >= p95Ms" }
            return RunConfig(profile, target, mode, p95, p99, transport)
        }
        fun fromSystem(): RunConfig = parse(System.getProperties().stringPropertyNames().associateWith { System.getProperty(it) })
    }
}

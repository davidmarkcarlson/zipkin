package com.cashly.locations.vdbs

import com.cashly.locations.LocationStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Connection settings for the vdbs backend.
 *
 * @property baseUrl vdbs base URL, e.g. "https://vdbs.cashtie.com".
 * @property apiKey vdbs API key. In real deployments source this from the environment
 *   / secret store, not config files.
 */
data class VdbsConfig(
    val baseUrl: String,
    val apiKey: String,
)

/**
 * Builds a vdbs-backed [LocationStore].
 *
 * This is *the* swap point. The rest of the system (the API, the JSON, the domain
 * model) depends only on [LocationStore]; which concrete store gets built lives here
 * and nowhere else. To move off vdbs to our own bulk-loaded data, write a
 * `BulkLocationStore : LocationStore` and call it from app wiring instead — no other
 * file changes.
 */
object VdbsLocationModule {

    /** The JSON config used to decode vdbs responses. Tolerant of additive changes. */
    fun jsonFormat(): Json = Json { ignoreUnknownKeys = true }

    /**
     * A Ktor client configured for vdbs. The caller owns its lifecycle and should
     * [HttpClient.close] it on shutdown; prefer sharing one client app-wide.
     *
     * `expectSuccess = false` so [VdbsClient] can see a 404 as a value (missing
     * location) rather than an exception.
     */
    fun httpClient(): HttpClient = HttpClient(CIO) {
        expectSuccess = false
        install(ContentNegotiation) { json(jsonFormat()) }
    }

    /** Wire a vdbs-backed store onto an existing client. */
    fun locationStore(http: HttpClient, config: VdbsConfig): LocationStore =
        VdbsLocationStore(http, config.baseUrl, config.apiKey)
}

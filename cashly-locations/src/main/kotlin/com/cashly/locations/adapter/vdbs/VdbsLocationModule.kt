package com.cashly.locations.adapter.vdbs

import com.cashly.locations.application.LocationProvider
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
 * Builds the vdbs outbound adapter.
 *
 * This is *the* swap point. The application core depends only on the
 * [LocationProvider] port; which concrete adapter gets built lives here and nowhere
 * else. To move off vdbs to cashly's own bulk-loaded data, write a
 * `BulkLocationProvider : LocationProvider` with its own module and call it from app
 * wiring instead — the use cases, the HTTP adapter, and the OpenAPI spec do not change.
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

    /** Wire a vdbs-backed [LocationProvider] onto an existing client. */
    fun locationProvider(http: HttpClient, config: VdbsConfig): LocationProvider =
        VdbsLocationProvider(http, config.baseUrl, config.apiKey)
}

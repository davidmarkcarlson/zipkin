package com.cashly.locations.adapter.vdbs

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess

/**
 * Thin transport client for cashtie's vdbs HTTP API.
 *
 * Responsibilities, and nothing more:
 *  - know vdbs' URL layout and how it wants to be authenticated
 *  - turn an HTTP exchange into a decoded wire object (or null for 404)
 *
 * It does no domain mapping and exposes no domain types — raw fields in, vdbs wire
 * types out. Mapping is [toLocation]'s job; deciding what to ask for is
 * [VdbsLocationProvider]'s. Keeping this class transport-only is what lets us swap the
 * whole vdbs backend out later without touching anyone.
 *
 * @param http Ktor client with JSON content negotiation installed. Injected so the
 *   provider stays testable and engine config lives in [VdbsLocationModule].
 * @param baseUrl vdbs base URL, e.g. "https://vdbs.cashtie.com".
 * @param apiKey vdbs API key, sent on every request.
 */
internal class VdbsClient(
    private val http: HttpClient,
    private val baseUrl: String,
    private val apiKey: String,
) {

    /** Issue a place search. */
    suspend fun search(
        text: String?,
        lat: Double?,
        lon: Double?,
        radiusMeters: Int?,
        limit: Int,
    ): VdbsSearchResponse {
        val response = http.get("$baseUrl/v1/places/search") {
            // Ktor omits parameters whose value is null, so optional criteria simply
            // fall away rather than being sent as empty.
            parameter("q", text)
            parameter("lat", lat)
            parameter("lon", lon)
            parameter("radius", radiusMeters)
            parameter("limit", limit)
            parameter("api_key", apiKey)
            accept(ContentType.Application.Json)
        }
        return response.requireSuccess().body()
    }

    /** Look up a single place by vdbs id. Returns null when vdbs answers 404. */
    suspend fun lookup(placeId: String): VdbsPlace? {
        val response = http.get("$baseUrl/v1/places/$placeId") {
            parameter("api_key", apiKey)
            accept(ContentType.Application.Json)
        }
        return when (response.status) {
            HttpStatusCode.NotFound -> null
            else -> response.requireSuccess().body()
        }
    }

    /**
     * Fail on any non-2xx status so the provider never tries to decode an error page
     * as a place. (The client is configured with `expectSuccess = false` so we can
     * spot 404 before this runs.)
     */
    private fun HttpResponse.requireSuccess(): HttpResponse {
        if (!status.isSuccess()) throw VdbsException("vdbs returned $status")
        return this
    }
}

/**
 * Raised for vdbs transport/protocol faults. `internal` so it never escapes the
 * module; [VdbsLocationProvider] decides how these surface to the core (today: as a
 * thrown exception, opaque to the use case and the API).
 */
internal class VdbsException(message: String) : RuntimeException(message)

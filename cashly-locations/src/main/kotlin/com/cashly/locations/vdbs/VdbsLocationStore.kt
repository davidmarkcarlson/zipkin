package com.cashly.locations.vdbs

import com.cashly.locations.Location
import com.cashly.locations.LocationQuery
import com.cashly.locations.LocationStore
import io.ktor.client.HttpClient

/**
 * A [LocationStore] backed by cashtie's vdbs service.
 *
 * The orchestration layer for the vdbs backend, and the only `vdbs` type the outside
 * world ever names. It:
 *  1. translates a domain [LocationQuery] into the raw parameters vdbs wants,
 *  2. delegates the HTTP exchange to [VdbsClient],
 *  3. (decoding happens via kotlinx.serialization in the client), and
 *  4. maps the result back to the domain via [toLocation].
 *
 * Callers only ever see [LocationStore]; that vdbs is involved at all is an
 * implementation detail. Swapping to a self-hosted, bulk-loaded backend later means
 * writing another [LocationStore] and changing one line of wiring — see README.md.
 */
class VdbsLocationStore internal constructor(
    private val client: VdbsClient,
) : LocationStore {

    /**
     * Public constructor: wire a vdbs store onto a Ktor client. The only way to build
     * one from outside the module, so callers never have to name the `internal`
     * [VdbsClient].
     */
    constructor(http: HttpClient, baseUrl: String, apiKey: String) :
        this(VdbsClient(http, baseUrl, apiKey))

    override suspend fun search(query: LocationQuery): List<Location> {
        val limit = query.limit.coerceIn(1, LocationQuery.MAX_LIMIT)
        val response = client.search(
            text = query.text,
            lat = query.near?.lat,
            lon = query.near?.lng,
            radiusMeters = query.radiusMeters,
            limit = limit,
        )
        return response.results.asSequence()
            .map { it.toLocation() }
            .take(limit)
            .toList()
    }

    override suspend fun get(id: String): Location? =
        client.lookup(id)?.toLocation()
}

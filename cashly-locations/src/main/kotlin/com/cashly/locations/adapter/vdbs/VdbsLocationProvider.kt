package com.cashly.locations.adapter.vdbs

import com.cashly.locations.application.LocationProvider
import com.cashly.locations.domain.Location
import com.cashly.locations.domain.LocationQuery
import io.ktor.client.HttpClient

/**
 * Outbound adapter: a [LocationProvider] backed by cashtie's vdbs service.
 *
 * The only `vdbs` type the outside world ever names, and the implementation of the
 * port the use cases depend on. Its job is pure translation:
 *  1. domain [LocationQuery] -> the raw parameters vdbs wants (delegated to [VdbsClient]),
 *  2. vdbs wire response -> domain via [toLocation].
 *
 * It holds no application rules — bounding the result limit, for example, is the
 * [com.cashly.locations.application.SearchLocations] use case's concern, not this
 * adapter's. Swapping to a self-hosted, bulk-loaded backend later means writing
 * another [LocationProvider] and changing one line of wiring — see README.md.
 */
class VdbsLocationProvider internal constructor(
    private val client: VdbsClient,
) : LocationProvider {

    /**
     * Public constructor: wire a vdbs provider onto a Ktor client. The only way to
     * build one from outside the module, so callers never name the `internal`
     * [VdbsClient].
     */
    constructor(http: HttpClient, baseUrl: String, apiKey: String) :
        this(VdbsClient(http, baseUrl, apiKey))

    override suspend fun search(query: LocationQuery): List<Location> {
        val response = client.search(
            text = query.text,
            lat = query.near?.lat,
            lon = query.near?.lng,
            radiusMeters = query.radiusMeters,
            limit = query.limit,
        )
        return response.results.map { it.toLocation() }
    }

    override suspend fun get(id: String): Location? =
        client.lookup(id)?.toLocation()
}

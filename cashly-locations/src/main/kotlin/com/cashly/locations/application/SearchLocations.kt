package com.cashly.locations.application

import com.cashly.locations.domain.Location
import com.cashly.locations.domain.LocationQuery

/**
 * Use case: search for locations.
 *
 * This is where the application rules for a search live — today, bounding the result
 * limit to a sane range — independent of how the request arrived (HTTP) or where the
 * data comes from (vdbs). The inbound adapter calls this; it calls out through the
 * [LocationProvider] port.
 */
class SearchLocations(private val locations: LocationProvider) {

    suspend operator fun invoke(query: LocationQuery): List<Location> {
        val limit = query.limit.coerceIn(1, LocationQuery.MAX_LIMIT)
        return locations.search(query.copy(limit = limit)).take(limit)
    }
}

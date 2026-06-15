package com.cashly.locations.application

import com.cashly.locations.domain.Location
import com.cashly.locations.domain.LocationQuery

/**
 * Outbound port: the application core's view of "something that can supply location
 * data".
 *
 * The use cases depend on this interface; outbound adapters implement it
 * (vdbs today via [com.cashly.locations.adapter.vdbs.VdbsLocationProvider], a
 * bulk-loaded store tomorrow). This is the seam that keeps the core ignorant of where
 * the data actually lives. The port is defined here, by the application, and owned by
 * it — adapters depend inward on it, never the other way around.
 *
 * Implementations must not surface backend-specific types or errors. Everything in
 * and out is the domain model.
 */
interface LocationProvider {

    /** Fetch locations matching [query]. An empty list means "no matches". */
    suspend fun search(query: LocationQuery): List<Location>

    /** Fetch a single location by opaque id, or null when there is no such location. */
    suspend fun get(id: String): Location?
}

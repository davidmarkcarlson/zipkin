package com.cashly.locations

/**
 * A request to search for locations.
 *
 * Expressed purely in domain terms: free text, an optional point to bias results
 * around, an optional radius, and a result limit. Intentionally decoupled from both
 * the HTTP query string that produced it and the backend query that will satisfy it,
 * so neither side leaks into the other.
 *
 * @property text free text to match against, e.g. "coffee".
 * @property near optional point to search around.
 * @property radiusMeters optional radius around [near], in meters. Ignored when
 *   [near] is null.
 * @property limit maximum number of results to return.
 */
data class LocationQuery(
    val text: String? = null,
    val near: LatLng? = null,
    val radiusMeters: Int? = null,
    val limit: Int = DEFAULT_LIMIT,
) {
    companion object {
        const val DEFAULT_LIMIT = 25
        const val MAX_LIMIT = 100
    }
}

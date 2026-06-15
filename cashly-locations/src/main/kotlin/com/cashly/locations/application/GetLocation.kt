package com.cashly.locations.application

import com.cashly.locations.domain.Location

/**
 * Use case: look up a single location by id.
 *
 * Trivial today — it just delegates to the [LocationProvider] port — but it exists so
 * the inbound adapter depends on a use case rather than reaching for the port
 * directly, and so any future lookup rules (caching policy, id normalization, ...)
 * have an obvious home that is neither HTTP nor vdbs.
 */
class GetLocation(private val locations: LocationProvider) {

    suspend operator fun invoke(id: String): Location? = locations.get(id)
}

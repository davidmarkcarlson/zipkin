package com.cashly.locations

/**
 * The seam between the locations API and whatever actually serves location data.
 *
 * A small, backend-agnostic interface that the rest of the system depends on. Today
 * the only implementation is the vdbs-backed
 * [com.cashly.locations.vdbs.VdbsLocationStore]; when cashly bulk loads and serves
 * location data itself, that becomes a second implementation of this same interface
 * and the API layer is none the wiser.
 *
 * Implementations must not surface backend-specific types or errors through this
 * interface. Everything in and out is expressed in the location domain model.
 */
interface LocationStore {

    /**
     * Search for locations matching [query], best matches first.
     *
     * Returns at most [LocationQuery.limit] results. An empty list means "no
     * matches", never an error condition.
     */
    suspend fun search(query: LocationQuery): List<Location>

    /**
     * Look up a single location by its opaque id, or null when no location has that
     * id. Reserve thrown exceptions for genuine faults (backend unreachable,
     * malformed response, ...).
     */
    suspend fun get(id: String): Location?
}

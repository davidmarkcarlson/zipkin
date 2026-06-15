package com.cashly.locations

/**
 * A WGS84 geographic coordinate, in decimal degrees.
 *
 * Part of the location domain model. Deliberately independent of any backend:
 * whether positions come from vdbs today or our own bulk-loaded data tomorrow,
 * callers always see a [LatLng].
 */
data class LatLng(
    val lat: Double,
    val lng: Double,
)

/**
 * A place callers can search for and look up.
 *
 * The canonical, backend-agnostic representation of a location, and the only
 * location type the API layer is allowed to see. The shape of the underlying data
 * source (vdbs, or a future bulk-loaded store) never reaches the caller.
 *
 * @property id opaque, stable identifier. Whatever backend produced the location is
 *   responsible for round-tripping this id back through [LocationStore.get].
 * @property name human readable display name, e.g. "Blue Bottle Coffee".
 * @property address formatted, single-line postal address.
 * @property position geographic position of the location.
 * @property categories zero or more normalized category tags, e.g. "cafe".
 */
data class Location(
    val id: String,
    val name: String,
    val address: String,
    val position: LatLng,
    val categories: List<String> = emptyList(),
)

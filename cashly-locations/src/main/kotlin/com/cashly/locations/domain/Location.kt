package com.cashly.locations.domain

/**
 * A WGS84 geographic coordinate, in decimal degrees.
 *
 * Pure domain: no dependency on any framework, transport, or backend. Whether
 * positions come from vdbs today or our own bulk-loaded data tomorrow, the rest of
 * the system sees only a [LatLng].
 */
data class LatLng(
    val lat: Double,
    val lng: Double,
)

/**
 * A place callers can search for and look up.
 *
 * The canonical, backend-agnostic representation of a location — the currency the
 * use cases and the API speak. The shape of the underlying data source never reaches
 * this type.
 *
 * @property id opaque, stable identifier. The provider that produced the location is
 *   responsible for round-tripping this id back through a lookup.
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

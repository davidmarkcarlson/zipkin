package com.cashly.locations.adapter.http

import com.cashly.locations.domain.Location
import kotlinx.serialization.Serializable

/**
 * The JSON shapes the HTTP adapter hands back to callers, mirroring the response
 * schema in api/locations-api.yaml.
 *
 * Kept as a view model local to this adapter — rather than serializing the domain
 * [Location] directly — so the wire contract and the internal model can evolve
 * independently.
 */
@Serializable
data class JsonLatLng(
    val lat: Double,
    val lng: Double,
)

@Serializable
data class JsonLocation(
    val id: String,
    val name: String,
    val address: String,
    val position: JsonLatLng,
    val categories: List<String>,
)

/** Maps the domain model onto this adapter's JSON view model. */
internal fun Location.toJson(): JsonLocation =
    JsonLocation(
        id = id,
        name = name,
        address = address,
        position = JsonLatLng(position.lat, position.lng),
        categories = categories,
    )

package com.cashly.locations.api

import com.cashly.locations.Location
import kotlinx.serialization.Serializable

/**
 * The JSON shapes the locations API hands back to callers, mirroring the response
 * schema in api/locations-api.yaml.
 *
 * Kept as a separate view model — rather than serializing the domain [Location]
 * directly — so the wire contract and the internal model can evolve independently.
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

/** Maps the domain model onto the API's JSON view model. The single boundary. */
internal fun Location.toJson(): JsonLocation =
    JsonLocation(
        id = id,
        name = name,
        address = address,
        position = JsonLatLng(position.lat, position.lng),
        categories = categories,
    )

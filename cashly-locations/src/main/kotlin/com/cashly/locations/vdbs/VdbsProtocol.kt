package com.cashly.locations.vdbs

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * vdbs (cashtie) wire model.
 *
 * These types describe the JSON vdbs actually returns. They are `internal`: nothing
 * outside this module should know vdbs uses `place_id`/`display_name`/`lon`, or that
 * it nests results under `results`. [toLocation] is the single place those names are
 * mapped onto the domain model.
 *
 * Decoding is tolerant by design (the client configures `ignoreUnknownKeys`), so
 * additive changes on the vdbs side don't break us. The assumed vdbs contract is
 * documented in README.md; adjust the field names here if the real contract differs.
 */
@Serializable
internal data class VdbsSearchResponse(
    val results: List<VdbsPlace> = emptyList(),
)

@Serializable
internal data class VdbsPlace(
    @SerialName("place_id") val placeId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("formatted_address") val formattedAddress: String = "",
    val lat: Double,
    val lon: Double,
    val categories: List<String> = emptyList(),
)

package com.cashly.locations.adapter.vdbs

import com.cashly.locations.domain.LatLng
import com.cashly.locations.domain.Location

/**
 * The one place vdbs' wire shape is mapped onto the location domain model.
 *
 * If vdbs renames a field or restructures its payload, this is the only file that
 * changes.
 */
internal fun VdbsPlace.toLocation(): Location =
    Location(
        id = placeId,
        name = displayName,
        address = formattedAddress,
        position = LatLng(lat, lon),
        categories = categories,
    )

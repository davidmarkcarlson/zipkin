/*
 * Copyright 2012 Twitter Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.twitter.zipkin.locations.vdbs

import com.twitter.zipkin.locations.{LatLng, Location}

/**
 * The one place vdbs' wire shape is mapped onto the location domain model.
 *
 * Following the same convention as the trace adapters (`ThriftAdapter`,
 * `JsonAdapter`): a single object whose only job is translating between one
 * representation and the canonical domain type. If vdbs renames a field or
 * restructures its payload, this is the only file that changes.
 */
private[vdbs] object VdbsLocationAdapter {

  def apply(place: VdbsPlace): Location = {
    Location(
      id = place.placeId,
      name = place.displayName,
      address = place.formattedAddress,
      position = LatLng(place.lat, place.lon),
      categories = place.categories)
  }
}

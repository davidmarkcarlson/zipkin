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
package com.twitter.zipkin.web.locations

import com.twitter.zipkin.locations.{LatLng, Location}
import com.twitter.zipkin.web.locations.json.{JsonLatLng, JsonLocation}

/**
 * Maps the domain location model onto the API's JSON view model.
 *
 * Same role as `JsonAdapter` for traces: the single boundary between the internal
 * representation and the public wire format.
 */
object LocationJsonAdapter {

  def apply(l: Location): JsonLocation =
    JsonLocation(l.id, l.name, l.address, apply(l.position), l.categories)

  def apply(p: LatLng): JsonLatLng =
    JsonLatLng(p.lat, p.lng)
}

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
package com.twitter.zipkin.locations

/**
 * A place that callers can search for and look up.
 *
 * This is the canonical, backend-agnostic representation of a location. It is the
 * only location type the API layer is allowed to see; the shape of the underlying
 * data source (vdbs, or a future bulk-loaded store) never reaches the caller.
 *
 * @param id          stable identifier, opaque to the caller. Whatever backend
 *                    produced the location is responsible for round-tripping this
 *                    id back through [[LocationStore.get]].
 * @param name        human readable display name, e.g. "Blue Bottle Coffee".
 * @param address     formatted, single-line postal address.
 * @param position    geographic position of the location.
 * @param categories  zero or more normalized category tags, e.g. "cafe".
 */
case class Location(
  id: String,
  name: String,
  address: String,
  position: LatLng,
  categories: Seq[String] = Nil)

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
 * A request to search for locations.
 *
 * Expressed purely in domain terms: free text, an optional point to bias/limit
 * results around, an optional radius, and a result limit. It is intentionally
 * decoupled from both the HTTP query string that produced it and the backend
 * query that will satisfy it, so neither side leaks into the other.
 *
 * @param text         free text to match against, e.g. "coffee".
 * @param near         optional point to search around.
 * @param radiusMeters optional radius around `near`, in meters. Ignored when
 *                     `near` is empty.
 * @param limit        maximum number of results to return.
 */
case class LocationQuery(
  text: Option[String] = None,
  near: Option[LatLng] = None,
  radiusMeters: Option[Int] = None,
  limit: Int = LocationQuery.DefaultLimit)

object LocationQuery {
  val DefaultLimit = 25
  val MaxLimit = 100
}

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
package com.twitter.zipkin.web.locations.json

/**
 * The JSON shape the locations API hands back to callers.
 *
 * These mirror the response schema in `doc/locations-api.yaml`. They exist as a
 * separate view model — rather than serializing the domain [[com.twitter.zipkin.locations.Location]]
 * directly — for the same reason the trace endpoints have `JsonSpan` etc.: the wire
 * contract and the internal model are allowed to evolve independently.
 */
case class JsonLatLng(lat: Double, lng: Double)

case class JsonLocation(
  id: String,
  name: String,
  address: String,
  position: JsonLatLng,
  categories: Seq[String])

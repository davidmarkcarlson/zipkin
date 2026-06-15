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

import org.codehaus.jackson.JsonNode
import org.codehaus.jackson.map.ObjectMapper
import scala.collection.JavaConverters._

/**
 * vdbs (cashtie) wire model and decoding.
 *
 * These types describe the JSON cashtie's vdbs service actually returns. They are
 * deliberately `private[vdbs]`: nothing outside this package should know vdbs uses
 * `place_id`/`display_name`/`lon`, or that it nests results under `results`. The
 * adapter ([[VdbsLocationAdapter]]) is the single place those names are mapped onto
 * the domain model.
 *
 * Decoding is tolerant by design — we read a JSON tree and pull out the fields we
 * care about rather than binding to a fixed schema, so additive changes on the vdbs
 * side don't break us. The vdbs contract assumed here is documented in
 * `doc/locations-backend.md`; adjust the field names below if the real contract
 * differs.
 */
private[vdbs] case class VdbsPlace(
  placeId: String,
  displayName: String,
  formattedAddress: String,
  lat: Double,
  lon: Double,
  categories: Seq[String])

private[vdbs] object VdbsProtocol {

  private[this] val mapper = new ObjectMapper

  /**
   * Parse a vdbs "search" response body into its list of places.
   *
   * Shape: {@code { "results": [ <place>, ... ] } }
   */
  def parseSearch(body: String): Seq[VdbsPlace] = {
    val root = mapper.readTree(body)
    nodeSeq(Option(root).map(_.get("results")).orNull).map(place)
  }

  /**
   * Parse a vdbs "lookup" response body into a single place.
   *
   * Shape: a bare {@code <place>} object.
   */
  def parsePlace(body: String): VdbsPlace = {
    place(mapper.readTree(body))
  }

  private[this] def place(node: JsonNode): VdbsPlace = {
    VdbsPlace(
      placeId = text(node, "place_id"),
      displayName = text(node, "display_name"),
      formattedAddress = text(node, "formatted_address"),
      lat = double(node, "lat"),
      lon = double(node, "lon"),
      categories = nodeSeq(node.get("categories")).map(_.asText))
  }

  private[this] def nodeSeq(node: JsonNode): Seq[JsonNode] =
    if (node == null || !node.isArray) Nil
    else node.getElements.asScala.toSeq

  private[this] def text(node: JsonNode, field: String): String = {
    val v = node.get(field)
    if (v == null || v.isNull) "" else v.asText
  }

  private[this] def double(node: JsonNode, field: String): Double = {
    val v = node.get(field)
    if (v == null || v.isNull) 0.0 else v.asDouble
  }
}

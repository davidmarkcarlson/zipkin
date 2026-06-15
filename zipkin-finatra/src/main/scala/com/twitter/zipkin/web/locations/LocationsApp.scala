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

import com.twitter.finatra.{Controller, Request}
import com.twitter.logging.Logger
import com.twitter.zipkin.locations.{LatLng, LocationQuery, LocationStore}

/**
 * Caller-facing HTTP API for locations, implementing `doc/locations-api.yaml`.
 *
 * This is the top of the stack. It depends only on the [[LocationStore]] seam and
 * the JSON view model — never on `vdbs`. It translates an HTTP request into a domain
 * [[LocationQuery]], asks the store, and renders the domain result as JSON. Point it
 * at a different `LocationStore` (e.g. a future bulk-loaded one) and this class does
 * not change.
 *
 * @param store the location backend. Injected; today a vdbs-backed store, see
 *              [[com.twitter.zipkin.config.LocationStoreConfig]].
 */
class LocationsApp(store: LocationStore) extends Controller {

  val log = Logger.get()

  /**
   * API: locations search
   * Returns locations matching the given criteria, best matches first.
   *
   * Optional GET params:
   * - query: String, free text to match
   * - lat, lng: Double, a point to search near (both required together)
   * - radius: Int, meters around (lat,lng); ignored without a point
   * - limit: Int, default LocationQuery.DefaultLimit, capped at LocationQuery.MaxLimit
   */
  get("/api/locations") { request =>
    parseQuery(request) match {
      case Right(query) =>
        store.search(query).map { locations =>
          render.json(locations.map(LocationJsonAdapter(_)))
        }
      case Left(message) =>
        render.status(400).body(message).toFuture
    }
  }

  /**
   * API: location lookup
   * Returns a single location by id, or 404 if there is no such location.
   *
   * Required path param:
   * - id: String
   */
  get("/api/locations/:id") { request =>
    val id = request.params("id")
    store.get(id).map {
      case Some(location) => render.json(LocationJsonAdapter(location))
      case None => render.status(404).body("No location with id " + id)
    }
  }

  /**
   * Translate the HTTP query string into a domain [[LocationQuery]], or a Left with
   * a client-facing error message for malformed input. Keeping this here means the
   * store only ever sees well-formed domain queries.
   */
  private def parseQuery(request: Request): Either[String, LocationQuery] = {
    for {
      near <- parseNear(request).right
      radius <- parseInt(request, "radius").right
      limit <- parseInt(request, "limit").right
    } yield {
      LocationQuery(
        text = request.params.get("query").map(_.trim).filter(_.nonEmpty),
        near = near,
        radiusMeters = radius,
        limit = limit.getOrElse(LocationQuery.DefaultLimit))
    }
  }

  private def parseNear(request: Request): Either[String, Option[LatLng]] = {
    (parseDouble(request, "lat"), parseDouble(request, "lng")) match {
      case (Right(Some(lat)), Right(Some(lng))) => Right(Some(LatLng(lat, lng)))
      case (Right(None), Right(None)) => Right(None)
      case (Left(m), _) => Left(m)
      case (_, Left(m)) => Left(m)
      case _ => Left("lat and lng must be provided together")
    }
  }

  private def parseInt(request: Request, name: String): Either[String, Option[Int]] =
    parseParam(request, name)(_.toInt)

  private def parseDouble(request: Request, name: String): Either[String, Option[Double]] =
    parseParam(request, name)(_.toDouble)

  private def parseParam[T](request: Request, name: String)(f: String => T): Either[String, Option[T]] =
    request.params.get(name) match {
      case None => Right(None)
      case Some(raw) =>
        try Right(Some(f(raw)))
        catch { case _: NumberFormatException => Left("Invalid value for " + name + ": " + raw) }
    }
}

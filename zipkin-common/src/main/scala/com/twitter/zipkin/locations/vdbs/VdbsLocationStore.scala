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

import com.twitter.finagle.Service
import com.twitter.logging.Logger
import com.twitter.util.Future
import com.twitter.zipkin.locations.{Location, LocationQuery, LocationStore}
import org.jboss.netty.handler.codec.http.{HttpRequest, HttpResponse}

/**
 * A [[LocationStore]] backed by cashtie's vdbs service.
 *
 * This is the orchestration layer for the vdbs backend, and the only `vdbs` type the
 * outside world ever names. It:
 *   1. translates a domain [[LocationQuery]] into the raw parameters vdbs wants,
 *   2. delegates the HTTP exchange to [[VdbsClient]],
 *   3. decodes the response with [[VdbsProtocol]], and
 *   4. maps it back to the domain via [[VdbsLocationAdapter]].
 *
 * Callers only ever see [[LocationStore]]; that vdbs is involved at all is an
 * implementation detail. Swapping to a self-hosted, bulk-loaded backend later means
 * writing another `LocationStore` and changing one line of config — see
 * `doc/locations-backend.md`.
 */
class VdbsLocationStore private[vdbs] (client: VdbsClient) extends LocationStore {

  private[this] val log = Logger.get()

  /**
   * Public constructor: wire a vdbs store directly onto a finagle HTTP service.
   * This is the only way to build one from outside the package, so callers never
   * have to name the package-private [[VdbsClient]].
   */
  def this(http: Service[HttpRequest, HttpResponse], apiKey: String) =
    this(new VdbsClient(http, apiKey))

  def search(query: LocationQuery): Future[Seq[Location]] = {
    val limit = math.min(math.max(query.limit, 1), LocationQuery.MaxLimit)
    client.search(
      text = query.text,
      lat = query.near.map(_.lat),
      lon = query.near.map(_.lng),
      radiusMeters = query.radiusMeters,
      limit = limit
    ).map { body =>
      VdbsProtocol.parseSearch(body).map(VdbsLocationAdapter(_)).take(limit)
    }.onFailure { e =>
      log.warning(e, "vdbs search failed for query: %s", query)
    }
  }

  def get(id: String): Future[Option[Location]] = {
    client.lookup(id).map { bodyOpt =>
      bodyOpt.map { body => VdbsLocationAdapter(VdbsProtocol.parsePlace(body)) }
    }.onFailure { e =>
      log.warning(e, "vdbs lookup failed for id: %s", id)
    }
  }

  def close() {
    // The finagle Service is owned by config (it may be shared); nothing to release
    // here today. Present so a future backend with its own resources fits the trait.
  }
}

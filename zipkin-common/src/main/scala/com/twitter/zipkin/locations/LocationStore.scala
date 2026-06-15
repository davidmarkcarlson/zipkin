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

import com.twitter.util.Future

/**
 * The seam between the location API and whatever actually serves location data.
 *
 * This mirrors the role [[com.twitter.zipkin.storage.Storage]] and
 * [[com.twitter.zipkin.storage.Index]] play for traces: a small, backend-agnostic
 * trait that the rest of the system depends on. Today the only implementation is
 * the vdbs-backed [[com.twitter.zipkin.locations.vdbs.VdbsLocationStore]]; when we
 * bulk load and serve location data ourselves, that becomes a second
 * implementation of this same trait and the API layer is none the wiser.
 *
 * Implementations must not surface backend-specific types or errors through this
 * interface. Everything in and out is expressed in the location domain model.
 */
trait LocationStore {

  /**
   * Search for locations matching `query`, best matches first.
   *
   * Returns at most `query.limit` results. An empty sequence means "no matches",
   * never an error condition.
   */
  def search(query: LocationQuery): Future[Seq[Location]]

  /**
   * Look up a single location by its opaque id.
   *
   * Resolves to `None` when no location has that id. Reserve a failed Future for
   * genuine faults (backend unreachable, malformed response, ...).
   */
  def get(id: String): Future[Option[Location]]

  /**
   * Release any resources (connections, clients) held by this store.
   */
  def close()
}

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
package com.twitter.zipkin.config

import com.twitter.finagle.Service
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.http.Http
import com.twitter.zipkin.locations.LocationStore
import com.twitter.zipkin.locations.vdbs.VdbsLocationStore
import org.jboss.netty.handler.codec.http.{HttpRequest, HttpResponse}

/**
 * Builds the [[LocationStore]] the web tier serves locations from.
 *
 * This is *the* swap point. The rest of the system (the API, the JSON, the domain
 * model) depends only on [[LocationStore]]; which concrete store gets built lives
 * here and nowhere else. To move off vdbs to our own bulk-loaded data, add a
 * `BulkLocationStoreConfig extends LocationStoreConfig` and select it from the
 * web config — no other file changes.
 */
trait LocationStoreConfig {
  def apply(): LocationStore
}

/**
 * A [[LocationStoreConfig]] that builds a vdbs-backed store.
 *
 * Owns the finagle HTTP client to cashtie's vdbs host; the store and everything
 * above it stay oblivious to the transport.
 *
 * @param host    vdbs host as "host:port", e.g. "vdbs.cashtie.com:443".
 * @param apiKey  vdbs API key.
 */
class VdbsLocationStoreConfig extends LocationStoreConfig {

  var host: String = "vdbs.cashtie.com:80"
  var apiKey: String = ""
  var hostConnectionLimit: Int = 4

  def apply(): LocationStore = {
    val http: Service[HttpRequest, HttpResponse] = ClientBuilder()
      .codec(Http())
      .hosts(host)
      .hostConnectionLimit(hostConnectionLimit)
      .build()
    new VdbsLocationStore(http, apiKey)
  }
}

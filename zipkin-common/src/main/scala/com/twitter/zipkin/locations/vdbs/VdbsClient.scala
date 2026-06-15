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
import com.twitter.util.Future
import java.net.URLEncoder
import org.jboss.netty.handler.codec.http.{DefaultHttpRequest, HttpMethod, HttpRequest,
  HttpResponse, HttpResponseStatus, HttpVersion}
import org.jboss.netty.util.CharsetUtil

/**
 * Thin transport client for cashtie's vdbs HTTP API.
 *
 * Responsibilities, and nothing more:
 *   - know vdbs' URL layout and how it wants to be authenticated
 *   - turn a finagle HTTP exchange into a raw response body (or `None` for 404)
 *
 * It deliberately does no domain mapping and exposes no domain types. It works in
 * raw request fields (text/lat/lon/...) and raw JSON strings; turning those into
 * [[com.twitter.zipkin.locations.Location]]s is [[VdbsLocationAdapter]]'s job, and
 * deciding what to ask for is [[VdbsLocationStore]]'s. Keeping this class transport-
 * only is what lets us swap the whole vdbs backend out later without touching anyone.
 *
 * @param http   finagle HTTP service pointed at the vdbs host. Injected so the
 *               store stays testable and connection management lives in config.
 * @param apiKey vdbs API key, sent on every request.
 */
private[vdbs] class VdbsClient(http: Service[HttpRequest, HttpResponse], apiKey: String) {

  import VdbsClient._

  /**
   * Issue a place search. Returns the raw response body.
   */
  def search(
    text: Option[String],
    lat: Option[Double],
    lon: Option[Double],
    radiusMeters: Option[Int],
    limit: Int
  ): Future[String] = {
    val params = Seq(
      text.map("q" -> _),
      lat.map("lat" -> _.toString),
      lon.map("lon" -> _.toString),
      radiusMeters.map("radius" -> _.toString),
      Some("limit" -> limit.toString)
    ).flatten
    get(SearchPath, params).map(requireBody(_))
  }

  /**
   * Look up a single place by vdbs id. Returns `None` when vdbs answers 404.
   */
  def lookup(placeId: String): Future[Option[String]] = {
    get(PlacePath + "/" + encode(placeId), Nil).map { response =>
      response.getStatus match {
        case HttpResponseStatus.NOT_FOUND => None
        case _ => Some(requireBody(response))
      }
    }
  }

  private[this] def get(path: String, params: Seq[(String, String)]): Future[HttpResponse] = {
    val withAuth = params :+ ("api_key" -> apiKey)
    val uri = path + "?" + withAuth.map { case (k, v) => encode(k) + "=" + encode(v) }.mkString("&")
    val request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri)
    request.setHeader("Accept", "application/json")
    http(request)
  }
}

private[vdbs] object VdbsClient {
  val SearchPath = "/v1/places/search"
  val PlacePath = "/v1/places"

  private def encode(s: String): String = URLEncoder.encode(s, "UTF-8")

  /**
   * Read a response body, failing the Future on any non-2xx status so the store
   * never tries to parse an error page as a place.
   */
  private def requireBody(response: HttpResponse): String = {
    val status = response.getStatus
    if (status.getCode / 100 != 2) {
      throw new VdbsException("vdbs returned " + status.getCode + " " + status.getReasonPhrase)
    }
    response.getContent.toString(CharsetUtil.UTF_8)
  }
}

/**
 * Raised for vdbs transport/protocol faults. Package-private so it never escapes
 * the backend; [[VdbsLocationStore]] is responsible for deciding how these surface
 * (today: as a failed Future, opaque to the caller).
 */
private[vdbs] class VdbsException(message: String) extends Exception(message)

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
import com.twitter.zipkin.locations.{LatLng, LocationQuery}
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.handler.codec.http.{DefaultHttpResponse, HttpRequest, HttpResponse,
  HttpResponseStatus, HttpVersion}
import org.jboss.netty.util.CharsetUtil
import org.specs.Specification

/**
 * Exercises the whole vdbs backend through its public seam ([[VdbsLocationStore]]
 * as a `LocationStore`), with a fake finagle service standing in for vdbs. Verifies
 * both directions of the translation: domain query -> vdbs request, vdbs response ->
 * domain locations.
 */
class VdbsLocationStoreSpec extends Specification {

  val searchBody =
    """{"results":[
       |  {"place_id":"abc","display_name":"Blue Bottle","formatted_address":"1 Main St",
       |   "lat":37.7,"lon":-122.4,"categories":["cafe","coffee"]},
       |  {"place_id":"def","display_name":"Philz","formatted_address":"2 Market St",
       |   "lat":37.8,"lon":-122.5,"categories":[]}
       |]}""".stripMargin

  val placeBody =
    """{"place_id":"abc","display_name":"Blue Bottle","formatted_address":"1 Main St",
       | "lat":37.7,"lon":-122.4,"categories":["cafe"]}""".stripMargin

  /** Records the last URI it was asked for and replies with a canned response. */
  class FakeVdbs(reply: HttpRequest => HttpResponse) extends Service[HttpRequest, HttpResponse] {
    var lastUri: String = ""
    def apply(request: HttpRequest): Future[HttpResponse] = {
      lastUri = request.getUri
      Future.value(reply(request))
    }
  }

  def response(status: HttpResponseStatus, body: String): HttpResponse = {
    val r = new DefaultHttpResponse(HttpVersion.HTTP_1_1, status)
    r.setContent(ChannelBuffers.copiedBuffer(body, CharsetUtil.UTF_8))
    r
  }

  def ok(body: String): HttpRequest => HttpResponse =
    _ => response(HttpResponseStatus.OK, body)

  "VdbsLocationStore" should {

    "transform vdbs search results into domain locations" in {
      val store = new VdbsLocationStore(new FakeVdbs(ok(searchBody)), "secret")

      val results = store.search(LocationQuery(text = Some("coffee"))).apply()

      results.size mustEqual 2
      val first = results.head
      first.id mustEqual "abc"
      first.name mustEqual "Blue Bottle"
      first.address mustEqual "1 Main St"
      first.position mustEqual LatLng(37.7, -122.4)
      first.categories mustEqual Seq("cafe", "coffee")
    }

    "translate a domain query into vdbs request params, including auth" in {
      val fake = new FakeVdbs(ok(searchBody))
      val store = new VdbsLocationStore(fake, "secret")

      store.search(LocationQuery(
        text = Some("coffee"),
        near = Some(LatLng(37.7, -122.4)),
        radiusMeters = Some(500),
        limit = 10)).apply()

      fake.lastUri must contain("/v1/places/search")
      fake.lastUri must contain("q=coffee")
      fake.lastUri must contain("lat=37.7")
      fake.lastUri must contain("lon=-122.4")
      fake.lastUri must contain("radius=500")
      fake.lastUri must contain("limit=10")
      fake.lastUri must contain("api_key=secret")
    }

    "honor the result limit even if vdbs returns more" in {
      val store = new VdbsLocationStore(new FakeVdbs(ok(searchBody)), "secret")
      store.search(LocationQuery(limit = 1)).apply().size mustEqual 1
    }

    "look up a single location by id" in {
      val store = new VdbsLocationStore(new FakeVdbs(ok(placeBody)), "secret")
      val location = store.get("abc").apply()
      location must beSome
      location.get.name mustEqual "Blue Bottle"
    }

    "return None when vdbs has no such location" in {
      val notFound: HttpRequest => HttpResponse =
        _ => response(HttpResponseStatus.NOT_FOUND, "not found")
      val store = new VdbsLocationStore(new FakeVdbs(notFound), "secret")
      store.get("missing").apply() mustEqual None
    }
  }
}

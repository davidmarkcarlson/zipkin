# Locations backend (first pass)

This is a first pass at serving **locations** through a layered backend whose first
implementation is cashtie's **vdbs** service. The whole point of the layering is that
we can later swap vdbs for data we bulk load and serve ourselves **without touching
the API or any caller**.

## Layers

```
HTTP caller
    │  doc/locations-api.yaml  (the only contract a caller sees)
    ▼
LocationsApp                         zipkin-finatra .../web/locations
    │  finatra Controller: parses the request, renders JSON
    │  depends on  ── LocationStore (trait)  +  JsonLocation (view model)
    ▼
LocationStore (trait)                zipkin-common .../locations         ◄── the seam
    │  search(LocationQuery): Future[Seq[Location]]
    │  get(id): Future[Option[Location]]
    ▼
VdbsLocationStore                    zipkin-common .../locations/vdbs
    │  1. LocationQuery        ─► vdbs request params
    │  2. VdbsClient           ─► HTTP exchange with vdbs (finagle-http)
    │  3. VdbsProtocol         ─► decode vdbs JSON  (package-private wire model)
    │  4. VdbsLocationAdapter  ─► vdbs place  ►  domain Location
    ▼
cashtie / vdbs
```

Everything vdbs-specific lives under the `...locations.vdbs` package and is
`private[vdbs]` wherever the language allows: the wire model (`VdbsPlace`), the JSON
decoding (`VdbsProtocol`), the transport (`VdbsClient`), and the mapping
(`VdbsLocationAdapter`). The only public name in that package is
`VdbsLocationStore`, and the only public *type* it exposes is `LocationStore`. So a
caller — including `LocationsApp` — literally cannot name a vdbs type. That is what
"don't leak implementation details" buys us, enforced by the compiler rather than by
convention.

## Where the seam is, and how to swap it

`LocationStore` is the seam, mirroring how `Storage`/`Index` abstract trace storage
over Cassandra. The concrete backend is chosen in exactly one place:
`LocationStoreConfig` (zipkin-finatra `.../config`).

To move off vdbs to our own bulk-loaded data:

1. Add `class BulkLocationStore(...) extends LocationStore` (new package, e.g.
   `...locations.bulk`).
2. Add `class BulkLocationStoreConfig extends LocationStoreConfig`.
3. Point the web config at it:
   `locationStoreConfig = Some(new BulkLocationStoreConfig { ... })`.

No change to `LocationsApp`, the JSON view model, the domain model, or
`doc/locations-api.yaml`. Callers see nothing.

## Wiring it on

The endpoints are **off by default** so existing deployments are unaffected — no vdbs
client is built unless a backend is configured. In a web config:

```scala
new ZipkinWebConfig {
  locationStoreConfig = Some(new VdbsLocationStoreConfig {
    host   = "vdbs.cashtie.com:80"
    apiKey = "..."   // from the environment in real deployments
  })
  // ...
}
```

With that set, `ZipkinWeb` registers `LocationsApp` and `/api/locations` is live.

## Assumptions to confirm

This is a first pass; the architecture is the deliverable, these details are not yet
pinned down:

- **vdbs contract.** Assumed `GET /v1/places/search?q=&lat=&lon=&radius=&limit=&api_key=`
  returning `{ "results": [ { place_id, display_name, formatted_address, lat, lon,
  categories } ] }`, and `GET /v1/places/{id}` returning a bare place (404 when
  absent). All of this is isolated to `VdbsClient` + `VdbsProtocol`; correcting it
  touches only those two files. Decoding is tolerant (reads a JSON tree, ignores
  unknown fields) so additive vdbs changes won't break us.
- **Auth.** Assumed an `api_key` query parameter. If vdbs wants a header or signed
  request, that change is confined to `VdbsClient`.
- **Build.** `zipkin-common` gained `finagle-http` (transport) and
  `jackson-mapper-asl` (decoding). See `project/Project.scala`.

## Not built yet

Deliberately out of scope for a first pass: caching/rate-limiting in front of vdbs,
paging, retries/timeouts/circuit-breaking policy on the finagle client, and richer
domain fields (hours, phone, ...). Each slots into an existing layer without
reshaping the design.

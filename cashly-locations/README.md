# cashly locations (first pass)

A first pass at serving **locations** through a backend-agnostic seam whose first
implementation is cashtie's **vdbs** service. The whole point of the layering is that
cashly can later swap vdbs for data it bulk loads and serves itself **without touching
the API or any caller**.

Stack: Kotlin + coroutines, **Ktor** (server routing + HTTP client),
**kotlinx.serialization**.

## Layers

```
HTTP caller
    │  api/locations-api.yaml   (the only contract a caller sees)
    ▼
locationRoutes(store)                api/LocationRoutes.kt
    │  Ktor routes: parse the request, render JSON
    │  depends on  ── LocationStore  +  JsonLocation (view model)
    ▼
LocationStore (interface)            LocationStore.kt                ◄── the seam
    │  suspend search(LocationQuery): List<Location>
    │  suspend get(id): Location?
    ▼
VdbsLocationStore                    vdbs/VdbsLocationStore.kt
    │  1. LocationQuery       ─► vdbs request params
    │  2. VdbsClient          ─► HTTP exchange with vdbs (Ktor client)
    │  3. kotlinx deserialize ─► VdbsPlace      (internal wire model)
    │  4. VdbsPlace.toLocation ─► domain Location
    ▼
cashtie / vdbs
```

Everything vdbs-specific lives in the `com.cashly.locations.vdbs` package and is
`internal`: the wire model (`VdbsPlace`/`VdbsSearchResponse`), the transport
(`VdbsClient`), the fault type (`VdbsException`), and the mapping
(`VdbsPlace.toLocation`). The only public types are `VdbsLocationStore` (whose public
constructor exposes only `HttpClient`/`String`) and the `VdbsConfig`/`VdbsLocationModule`
wiring. A caller — including the routes — literally cannot reference a vdbs wire type:
Kotlin's `internal` visibility enforces non-leakage at module scope, so it's checked
by the compiler, not by convention.

## Where the seam is, and how to swap it

`LocationStore` is the seam. The concrete backend is chosen in exactly one place —
app wiring — via `VdbsLocationModule`.

To move off vdbs to cashly's own bulk-loaded data:

1. Add `class BulkLocationStore(...) : LocationStore` (e.g. a `...locations.bulk`
   package).
2. Build it in app wiring instead of the vdbs one.

No change to `locationRoutes`, the JSON view model, the domain model, or
`api/locations-api.yaml`. Callers see nothing.

## Wiring it into a Ktor app

```kotlin
val http = VdbsLocationModule.httpClient()                 // share app-wide; close on shutdown
val store = VdbsLocationModule.locationStore(
    http,
    VdbsConfig(baseUrl = "https://vdbs.cashtie.com", apiKey = System.getenv("VDBS_API_KEY")),
)

embeddedServer(CIO, port = 8080) {
    install(ContentNegotiation) { json() }                 // server-side JSON
    routing { locationRoutes(store) }
}.start(wait = true)
```

## Assumptions to confirm

This is a first pass; the architecture is the deliverable, these details are not yet
pinned down:

- **vdbs contract.** Assumed `GET /v1/places/search?q=&lat=&lon=&radius=&limit=&api_key=`
  returning `{ "results": [ { place_id, display_name, formatted_address, lat, lon,
  categories } ] }`, and `GET /v1/places/{id}` returning a bare place (404 when
  absent). All of this is isolated to `VdbsClient` + `VdbsProtocol`; correcting it
  touches only those two files. Decoding uses `ignoreUnknownKeys`, so additive vdbs
  changes won't break us.
- **Auth.** Assumed an `api_key` query parameter. A header or signed request would be
  confined to `VdbsClient`.

## Not built yet

Deliberately out of scope for a first pass: caching / rate-limiting in front of vdbs,
paging, retry / timeout / circuit-breaking policy on the Ktor client, auth on the
public endpoints, and richer domain fields (hours, phone, ...). Each slots into an
existing layer without reshaping the design.

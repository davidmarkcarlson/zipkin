# cashly locations (first pass)

A first pass at serving **locations** through a hexagonal (ports & adapters) design
whose first outbound adapter is cashtie's **vdbs** service. The point of the layering
is that cashly can later swap vdbs for data it bulk loads and serves itself **without
touching the use cases, the API, or any caller**.

Stack: Kotlin + coroutines, **Ktor** (server routing + HTTP client),
**kotlinx.serialization**.

## Layers

```
HTTP caller
    │  api/locations-api.yaml
    ▼
adapter.http   LocationRoutes              inbound (HTTP) adapter
    │  parse request -> domain, render domain -> JSON; no logic
    ▼  calls a use case
application    SearchLocations / GetLocation   the core: application rules
    │  e.g. bound the result limit; knows nothing of HTTP or vdbs
    ▼  calls out through a port
application    LocationProvider (interface)    outbound port            ◄── the seam
    ▲  implemented by
adapter.vdbs   VdbsLocationProvider          outbound (vdbs) adapter
    │  domain query -> vdbs request (VdbsClient), vdbs JSON -> domain
    ▼
cashtie / vdbs
```

Dependencies point inward: `adapter.http` and `adapter.vdbs` depend on `application`,
which depends on `domain`, which depends on nothing. The vdbs adapter depends on the
`LocationProvider` port — the port does not depend on it.

### Packages

| Package | Role | Key types |
|---|---|---|
| `domain` | Entities, no dependencies | `Location`, `LatLng`, `LocationQuery` |
| `application` | Use cases + outbound port | `SearchLocations`, `GetLocation`, `LocationProvider` |
| `adapter.http` | Inbound HTTP adapter | `locationRoutes`, `JsonLocation` |
| `adapter.vdbs` | Outbound vdbs adapter | `VdbsLocationProvider`, `VdbsClient`, `VdbsLocationModule` |

## What lives where (and why it's not all in one place)

- **The route** only translates the HTTP edge: query string → `LocationQuery`, domain
  → JSON, status codes. No rules.
- **The use case** owns application rules. Today that's bounding the result limit to
  `[1, MAX_LIMIT]` — note this is *not* in the vdbs adapter, so it holds for any
  future backend and is unit-testable with no HTTP and no vdbs (see
  `SearchLocationsTest`).
- **The vdbs adapter** only translates to/from vdbs. Everything vdbs-specific — the
  wire model, the Ktor transport, the fault type, the mapper — is Kotlin `internal`,
  so the core literally cannot reference a vdbs type. The compiler enforces the
  boundary, not convention.

## Where the seam is, and how to swap it

`LocationProvider` is the seam. The concrete adapter is chosen in exactly one place —
app wiring — via `VdbsLocationModule`. To move off vdbs to cashly's own bulk-loaded
data:

1. Add `class BulkLocationProvider(...) : LocationProvider` in its own adapter package.
2. Build it in app wiring instead of the vdbs one.

No change to the use cases, `locationRoutes`, the domain model, or
`api/locations-api.yaml`. Callers see nothing.

## Wiring it into a Ktor app

```kotlin
// outbound adapter
val http = VdbsLocationModule.httpClient()                 // share app-wide; close on shutdown
val provider = VdbsLocationModule.locationProvider(
    http,
    VdbsConfig(baseUrl = "https://vdbs.cashtie.com", apiKey = System.getenv("VDBS_API_KEY")),
)

// application core
val searchLocations = SearchLocations(provider)
val getLocation = GetLocation(provider)

// inbound adapter
embeddedServer(CIO, port = 8080) {
    install(ContentNegotiation) { json() }
    routing { locationRoutes(searchLocations, getLocation) }
}.start(wait = true)
```

## Assumptions to confirm

This is a first pass; the architecture is the deliverable, these details are not yet
pinned down:

- **vdbs contract.** Assumed `GET /v1/places/search?q=&lat=&lon=&radius=&limit=&api_key=`
  returning `{ "results": [ { place_id, display_name, formatted_address, lat, lon,
  categories } ] }`, and `GET /v1/places/{id}` returning a bare place (404 when
  absent). All isolated to `VdbsClient` + `VdbsProtocol`; correcting it touches only
  those two files. Decoding uses `ignoreUnknownKeys`, so additive vdbs changes won't
  break us.
- **Auth.** Assumed an `api_key` query parameter. A header or signed request would be
  confined to `VdbsClient`.

## Not built yet

Deliberately out of scope for a first pass: caching / rate-limiting in front of vdbs,
paging, retry / timeout / circuit-breaking policy on the Ktor client, auth on the
public endpoints, and richer domain fields (hours, phone, ...). Each slots into an
existing layer without reshaping the design.

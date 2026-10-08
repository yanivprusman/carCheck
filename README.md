# carCheck — בדיקת רכב

Type an Israeli registration number, get everything the Ministry of Transport
publishes about that vehicle: make and model, year, colour, licence expiry,
ownership and every change of hands, odometer at the last test, structural /
colour / gas-conversion flags, open recalls with the importer's phone, engine
and towing figures, safety systems, pollution group, and the importer's list
price when it was new.

## Where the data comes from

Every registry is a CKAN datastore on data.gov.il (`datastore_search`, keyed by
`mispar_rechev`). The phone queries them **directly** — a lookup works from any
network, with nothing at home switched on. Resource ids and what each file
holds are documented in `mobile/shared/.../data/GovIl.kt`; the joins (model
spec, list price, recall notices are keyed by model codes, not by plate) live in
`VehicleLookup.kt`.

The main private-car file is replaced daily and is **empty for hours while it
reloads**. The app tells the difference between "no such plate" and "the
registry is mid-reload" by probing the file's row count, and says which.

## Photos of the model

The registry has no photo of any car, so the report shows photos of the **model**, labelled as
such and with their source. The phone asks this app's backend (`GET /api/photos?make=&model=
&name=&year=&kind=`), which decides where they come from and keeps them under
`/var/lib/carcheck/photos/<key>/`, so each lookup happens once and every later plate of that
model and year is answered from disk (`lib/photo-store.ts`):

- **Wikipedia** (`lib/wikipedia-photos.ts`) when the registry gives a commercial name and an
  article about exactly that make and model exists: editor-chosen, freely licensed photos, the
  car's own generation first (Commons file names start with their years).
- **Google Images** (`lib/google-photos.ts`) for everything else — mostly vehicles over 3.5 t,
  which the registry names only by a type code. The search runs in a real Chrome on an Xvfb
  display (`scripts/google-images.mjs`; Google refuses plain fetches and puppeteer-launched
  browsers), with its own profile at `/var/lib/carcheck/chrome-profile`. A result is shown only if
  its page names the car's year, and then either the model code (when it is distinctive, e.g.
  MAN's "12.163") or the model name most results agree on (Google resolves Fiat's "250" to
  "דוקאטו"). MAN codes are searched as MAN writes them ("12163LL" → "12.163"), and a vague code
  gets the vehicle kind added ("פיאט 250 2023 רכב מסחרי"). Searching the plate number itself
  finds nothing about the car.

`npm test` covers the selection rules.

## Layout

- `mobile/` — the app. KMP / Compose Multiplatform: all UI, models and the
  lookup are in `shared/commonMain`; `app/` is the Android launcher, a ViewModel
  wrapper, share/dial/copy intents, and the dev-flavour feedback widget.
- `app/`, `lib/` — the Next.js side: this landing page, the registry copy, the model
  photos, and the feedback-lib backend the dev-flavour widget reports to.

## Build and install

```bash
androidDeploy carCheck            # commit first: the APK embeds the commit hash
```

Or by hand: `cd mobile && ./gradlew :app:assembleDevDebug`, then install
`app/build/outputs/apk/dev/debug/app-dev-debug.apk` with
`/opt/automateLinux/utilities/chunked-adb-install.sh` (never a raw
`adb install` over the WireGuard serial).

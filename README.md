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

## Layout

- `mobile/` — the app. KMP / Compose Multiplatform: all UI, models and the
  lookup are in `shared/commonMain`; `app/` is the Android launcher, a ViewModel
  wrapper, share/dial/copy intents, and the dev-flavour feedback widget.
- `app/`, `lib/` — the Next.js side: this landing page and the feedback-lib
  backend the dev-flavour widget reports to. It serves no vehicle data.

## Build and install

```bash
androidDeploy carCheck            # commit first: the APK embeds the commit hash
```

Or by hand: `cd mobile && ./gradlew :app:assembleDevDebug`, then install
`app/build/outputs/apk/dev/debug/app-dev-debug.apk` with
`/opt/automateLinux/utilities/chunked-adb-install.sh` (never a raw
`adb install` over the WireGuard serial).

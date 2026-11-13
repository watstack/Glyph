# Glyph Sky

Weather and flight tracker for the **Nothing Phone (4a) Pro** Glyph Matrix (also works on Phone (3)). It shows the current temperature and an animated weather icon, and when a plane flies overhead it shows the plane.

The matrix shows the temperature for 30 seconds, then the weather icon animates for 3 seconds, on repeat. When the icon is a sun (clear or partly cloudy by day), the UV index follows for 3 seconds: a small "UV" with the number underneath.

Flipping the phone face down starts the cycle at the icon straight away, and fetches fresh weather and UV if what it has is more than 10 minutes old.

- One or two characters (`18`, `-5`, `7°`) use a 5×7 font; three (`-12`, `103`) use a 3×5 font. Everything stays inside the round 137-LED matrix.
- Icons, picked from Open-Meteo's WMO weather code: clear (sun by day, moon and stars at night), partly cloudy (cloud with sun or moon), cloudy, fog, drizzle, rain, heavy rain, sleet, snow and thunderstorm (cloud with lightning bolt). Clouds behind rain, snow and lightning are dimmed so what's falling stands out.
- The app shows the same icon animating, with the temperature, condition and UV index.
- Weather comes from [Open-Meteo](https://open-meteo.com) (free, no API key) and refreshes every 30 minutes.
- Location is the phone's *approximate* (city-level) location, rounded to ~1 km before it is sent to Open-Meteo, adsb.lol or airplanes.live. Opening the app refreshes it.

### Planes overhead

While the phone is face down, it checks for planes every 15 seconds. When one is within **5 km**, it interrupts the weather:

1. The plane flies across the matrix in the direction it's really heading (snapped to the nearest of 8 compass points), with a contrail, twice.
2. Its flight number, route and aircraft type scroll past, e.g. `QF401 MEL>SYD B738`. Anything unknown is left out; with no route it shows the callsign (`QFA401`).
3. Its altitude shows for 3 seconds: `35K` is 35,000 ft; below 1,000 ft it's in hundreds (`800`).

Then the weather cycle starts again from the temperature. Each plane is shown once per pass (not again for 10 minutes).

- Positions come from [adsb.lol](https://adsb.lol), falling back to [airplanes.live](https://airplanes.live) (both free community ADS-B feeds, no API key). Planes on the ground are ignored.
- Routes come from [adsbdb.com](https://www.adsbdb.com) (free). Routes are matched on callsign, so occasionally one is out of date.
- The app shows the last plane seen overhead, and has a **Show planes overhead** switch to turn this off.
- The phone is kept awake (a partial wake lock, at most 30 s) while a plane plays, so it can't fall asleep after the crossing and skip the flight info. `adb logcat -s GlyphSky` logs each plane and each step of its sequence.

### Simulate

The app's preview plays exactly what the matrix shows (with the temperature screen cut to 3 seconds). Under **Simulate**:

- **Weather picker**: choose any of the 12 icons (or Live weather) to watch it in the preview, with the temperature and UV screens.
- **Find nearest plane**: looks up the nearest real plane in the air within 50 km and plays its full sequence: crossing, flight info, altitude.
- **Show on Glyph**: plays the weather or plane you picked on the real matrix. Tap it, then put the phone face down within a minute (or it plays straight away if the toy is already showing). A simulated weather plays once, then live weather comes back.
- The app's name changed from Glyph Weather to Glyph Sky, but it's the same app (same package and signing key), so it updates over the top. The toy is now called **Weather & Flights**; if it ever disappears from the Always-on list, pick it again.

## Install (once)

Updates come through [Obtainium](https://github.com/ImranR98/Obtainium), a free app that installs Android apps straight from GitHub releases.

1. Install Obtainium from its [releases page](https://github.com/ImranR98/Obtainium/releases) (allow installing from unknown sources when asked).
2. In Obtainium tap **Add App**, enter `https://github.com/watstack/Glyph` and tap **Add**, then **Install**.
3. Open **Glyph Sky**, allow *Approximate* location.
4. Tap **Activate Glyph Toy** and add **Weather & Flights**.
5. Go to **Settings › Glyph Interface › Flip to Glyph › Always-on Glyph Toy** and pick **Weather & Flights**.
6. Put the phone face down. The weather icon starts straight away.

If you installed an older copy from an Actions artifact, uninstall it first, once: those were signed with a throwaway key.

## Updating

Every push to `main` publishes a release (`v0.3.<build number>`) signed with the same key, so it installs over the top and keeps your location permission, °C/°F choice and toy setup. Obtainium checks for new releases and notifies you; tap **Update**. Branch builds are under **Actions → Build APK** as the `glyph-weather-apk` artifact.

The signing key lives in two repository secrets, `GLYPH_KEYSTORE_B64` (the base64 of a PKCS12 keystore, alias `glyph`) and `GLYPH_KEYSTORE_PASSWORD`. Without them the build still works but uses a throwaway key and publishes no release. Keep a copy of the keystore: losing it means one more uninstall to switch to a new key.

## Build locally

```sh
bash scripts/fetch-sdk.sh      # downloads Nothing's Glyph Matrix SDK into app/libs/
./gradlew assembleDebug        # needs the Android SDK (Android Studio or command-line tools)
# to sign with the release key, set GLYPH_KEYSTORE (path) and GLYPH_KEYSTORE_PASSWORD first
./gradlew testDebugUnitTest    # font, UV, plane, ticker, altitude and flight-parsing tests
```

The Glyph Matrix SDK is © Nothing Technology Limited and licensed for non-commercial use only. It is not redistributed in this repo; see the
[GlyphMatrix Developer Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit).

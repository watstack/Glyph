# Glyph Weather

Shows the current temperature and an animated weather icon on the **Nothing Phone (4a) Pro** Glyph Matrix (also works on Phone (3)).

The matrix shows the temperature for 30 seconds, then the weather icon animates for 3 seconds, on repeat. When the icon is a sun (clear or partly cloudy by day), the UV index follows for 3 seconds: a small "UV" with the number underneath.

Flipping the phone face down starts the cycle at the icon straight away, and fetches fresh weather and UV if what it has is more than 10 minutes old.

- One or two characters (`18`, `-5`, `7°`) use a 5×7 font; three (`-12`, `103`) use a 3×5 font. Everything stays inside the round 137-LED matrix.
- Icons, picked from Open-Meteo's WMO weather code: clear (sun by day, moon and stars at night), partly cloudy (cloud with sun or moon), cloudy, fog, drizzle, rain, heavy rain, sleet, snow and thunderstorm (cloud with lightning bolt). Clouds behind rain, snow and lightning are dimmed so what's falling stands out.
- The app shows the same icon animating, with the temperature, condition and UV index.
- Weather comes from [Open-Meteo](https://open-meteo.com) (free, no API key) and refreshes every 30 minutes.
- Location is the phone's *approximate* (city-level) location, rounded to ~1 km before it is sent to Open-Meteo. Opening the app refreshes it.

## Install (once)

Updates come through [Obtainium](https://github.com/ImranR98/Obtainium), a free app that installs Android apps straight from GitHub releases.

1. Install Obtainium from its [releases page](https://github.com/ImranR98/Obtainium/releases) (allow installing from unknown sources when asked).
2. In Obtainium tap **Add App**, enter `https://github.com/watstack/Glyph` and tap **Add**, then **Install**.
3. Open **Glyph Weather**, allow *Approximate* location.
4. Tap **Activate Glyph Toy** and add **Weather**.
5. Go to **Settings › Glyph Interface › Flip to Glyph › Always-on Glyph Toy** and pick **Weather**.
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
./gradlew testDebugUnitTest    # font, UV screen and icon layout tests
```

The Glyph Matrix SDK is © Nothing Technology Limited and licensed for non-commercial use only. It is not redistributed in this repo; see the
[GlyphMatrix Developer Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit).

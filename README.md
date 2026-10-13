# Glyph Weather

Shows the current temperature on the **Nothing Phone (4a) Pro** Glyph Matrix (also works on Phone (3)).

- One or two characters (`18`, `-5`, `7°`) use a 5×7 font; three (`-12`, `103`) use a 3×5 font. Everything stays inside the round 137-LED matrix.
- Weather comes from [Open-Meteo](https://open-meteo.com) (free, no API key) and refreshes every 30 minutes.
- Location is the phone's *approximate* (city-level) location, rounded to ~1 km before it is sent to Open-Meteo. Opening the app refreshes it.

## Install

1. Open the latest run under **Actions → Build APK**, download the `glyph-weather-apk` artifact and unzip it.
2. Copy `app-debug.apk` to the phone and open it (allow installing from unknown sources when asked).
3. Open **Glyph Weather**, allow *Approximate* location.
4. Tap **Activate Glyph Toy** and add **Weather**.
5. Go to **Settings › Glyph Interface › Flip to Glyph › Always-on Glyph Toy** and pick **Weather**.
6. Put the phone face down. The temperature appears within about a minute.

## Build locally

```sh
bash scripts/fetch-sdk.sh      # downloads Nothing's Glyph Matrix SDK into app/libs/
./gradlew assembleDebug        # needs the Android SDK (Android Studio or command-line tools)
./gradlew testDebugUnitTest    # font layout tests
```

The Glyph Matrix SDK is © Nothing Technology Limited and licensed for non-commercial use only. It is not redistributed in this repo; see the
[GlyphMatrix Developer Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit).

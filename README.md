# Glyph Weather

Shows the current temperature and an animated weather icon on the **Nothing Phone (4a) Pro** Glyph Matrix (also works on Phone (3)).

The weather icon animates on the matrix for 5 seconds, then the temperature shows for 55 seconds, on repeat.

- Every temperature keeps its degree sign: one digit (`7°`) uses a 5×7 font, two characters (`18°`, `-5°`) a 4×7 font with a one-LED degree dot, three (`-12°`, `103°`) a 3×5 font with the dot. Everything stays inside the round 137-LED matrix.
- Icons, picked from Open-Meteo's WMO weather code: clear (sun by day, moon and stars at night), partly cloudy (cloud with sun or moon), cloudy, fog, drizzle, rain, heavy rain, sleet, snow and thunderstorm (cloud with lightning bolt). Clouds behind rain, snow and lightning are dimmed so what's falling stands out.
- In the style of Nothing's Moon Phase toy, icons are shaded across many brightness levels, whatever isn't lit right now (the moon's dark side, sun rays, the bolt, rain paths) keeps a faint glow, and each icon opens with a 1 s sweep before looping at 8 fps.
- The app shows the same icon animating, with the temperature and condition.
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
./gradlew testDebugUnitTest    # font and icon layout tests
```

The Glyph Matrix SDK is © Nothing Technology Limited and licensed for non-commercial use only. It is not redistributed in this repo; see the
[GlyphMatrix Developer Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit).

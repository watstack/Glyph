#!/usr/bin/env bash
# Downloads Nothing's Glyph Matrix SDK into app/libs/.
# The SDK's licence forbids redistributing it, so it is not committed to this repo.
set -euo pipefail

COMMIT=999b1143a6bff0dd79db11d21b92c15c56a346b1
SHA256=be00ee9cd7115f6b11984c6e31fe98e298fb726940d1555063610685ef3bbf29
URL="https://raw.githubusercontent.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit/${COMMIT}/glyph-matrix-sdk-2.0.aar"
DEST="$(cd "$(dirname "$0")/.." && pwd)/app/libs/glyph-matrix-sdk-2.0.aar"

mkdir -p "$(dirname "$DEST")"
curl -fsSL "$URL" -o "$DEST"
echo "${SHA256}  ${DEST}" | sha256sum -c -

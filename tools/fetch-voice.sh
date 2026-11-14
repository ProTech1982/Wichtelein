#!/usr/bin/env bash
# Lädt die Wichtelstimme (offizielle Quellen, mit SHA-256-Prüfung):
#  - sherpa-onnx (Apache-2.0) als Android-Bibliothek  -> app/libs/
#  - Piper-Stimme "Thorsten emotional" (CC0)          -> app/src/main/assets/voice/
# Beides liegt bewusst nicht im Git-Repository. Aufruf: bash tools/fetch-voice.sh
set -euo pipefail
cd "$(dirname "$0")/.."

AAR_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar"
AAR_SHA="633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96"
MODEL_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-de_DE-thorsten_emotional-medium.tar.bz2"
MODEL_SHA="4e55e6c7ba21700b6fb71e9544e1615bb348f74114799d23395826fdb33ee6f7"

fetch() { # url sha ziel
  local url="$1" sha="$2" dest="$3"
  if [ -f "$dest" ] && echo "$sha  $dest" | sha256sum -c - >/dev/null 2>&1; then return 0; fi
  echo "Lade $(basename "$dest") ..."
  curl -L --fail --retry 3 -o "$dest" "$url"
  echo "$sha  $dest" | sha256sum -c -
}

mkdir -p app/libs app/src/main/assets/voice
fetch "$AAR_URL" "$AAR_SHA" app/libs/sherpa-onnx-1.13.8.aar

if [ ! -f app/src/main/assets/voice/model.onnx ]; then
  tmp="$(mktemp -d)"
  fetch "$MODEL_URL" "$MODEL_SHA" "$tmp/model.tar.bz2"
  tar xjf "$tmp/model.tar.bz2" -C "$tmp"
  src="$tmp/vits-piper-de_DE-thorsten_emotional-medium"
  cp "$src/de_DE-thorsten_emotional-medium.onnx" app/src/main/assets/voice/model.onnx
  cp "$src/tokens.txt" app/src/main/assets/voice/tokens.txt
  rm -rf app/src/main/assets/voice/espeak-ng-data
  cp -r "$src/espeak-ng-data" app/src/main/assets/voice/espeak-ng-data
  rm -rf "$tmp"
fi
echo "Wichtelstimme bereit."

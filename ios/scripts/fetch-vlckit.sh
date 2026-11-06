#!/bin/bash
# Lädt MobileVLCKit (offizielle Version von VideoLAN, wie im CocoaPods-Rezept) nach ios/Frameworks.
# Damit spielt das iPhone IPTV-Sender als MPEG-TS sowie MKV/AVI ab, was AVPlayer nicht kann.
# Die Prüfsumme stammt aus dem offiziellen Rezept MobileVLCKit 3.7.5.
set -euo pipefail

VERSION="3.7.5"
URL="https://download.videolan.org/pub/cocoapods/prod/MobileVLCKit-3.7.5-a213fd11-6de05adc.tar.xz"
SHA256="a76f7a6cfcde4209b56f943e6e18400659d8a7a93ff482f8b1d059468d11e6be"

cd "$(dirname "$0")/.."
TARGET="Frameworks/MobileVLCKit.xcframework"
if [ -f "Frameworks/.vlckit-$VERSION" ] && [ -d "$TARGET" ]; then
  echo "MobileVLCKit $VERSION ist schon da."
  exit 0
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
echo "Lade MobileVLCKit $VERSION …"
curl -fsSL --retry 3 -o "$WORK/vlckit.tar.xz" "$URL"
echo "$SHA256  $WORK/vlckit.tar.xz" | shasum -a 256 -c -
tar -xJf "$WORK/vlckit.tar.xz" -C "$WORK" "MobileVLCKit-binary/MobileVLCKit.xcframework"

rm -rf Frameworks
mkdir -p Frameworks
mv "$WORK/MobileVLCKit-binary/MobileVLCKit.xcframework" "$TARGET"
# Debug-Symbole und alte 32-Bit-Teile weglassen: Die App wird damit deutlich kleiner
find "$TARGET" -name dSYMs -type d -prune -exec rm -rf {} +
# … und den Verweis darauf aus der Beschreibung entfernen, sonst bricht Xcode ab
COUNT=$(/usr/libexec/PlistBuddy -c "Print :AvailableLibraries" "$TARGET/Info.plist" | grep -c "LibraryIdentifier")
for ((i = 0; i < COUNT; i++)); do
  /usr/libexec/PlistBuddy -c "Delete :AvailableLibraries:$i:DebugSymbolsPath" "$TARGET/Info.plist" 2>/dev/null || true
done
DEVICE_BIN="$TARGET/ios-arm64_armv7_armv7s/MobileVLCKit.framework/MobileVLCKit"
if command -v lipo >/dev/null 2>&1 && lipo -info "$DEVICE_BIN" | grep -q armv7; then
  lipo "$DEVICE_BIN" -thin arm64 -output "$WORK/MobileVLCKit-arm64"
  mv "$WORK/MobileVLCKit-arm64" "$DEVICE_BIN"
fi
touch "Frameworks/.vlckit-$VERSION"
echo "MobileVLCKit $VERSION bereit: $(du -sh "$TARGET" | cut -f1)"

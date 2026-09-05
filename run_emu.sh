#!/usr/bin/env bash
# Run arm64 release on x86_64 emulator
set -euo pipefail
flutter build apk --release
flutter run --use-application-binary=build/app/outputs/flutter-apk/app-release.apk "$@"

#!/usr/bin/env sh

#
# Simplified gradlew placeholder.
#
# Real Android projects ship the full Gradle wrapper jar (~60KB) plus this
# script. Because the distribution jar is binary and we cannot easily
# download it in a sandboxed agent environment, this stub still lets you
# invoke gradle on a machine that has the gradle distribution installed:
#
#   1. Install Gradle 8.5+ from https://gradle.org/releases/ and put it on PATH.
#   2. From the project root run:   gradle :app:assembleDebug
#
# Or, in Android Studio: just open the project and let it generate the real
# wrapper (`File > Sync with File System` or run `gradle wrapper` once).
#
# This file is intentionally NOT executable. The real `gradlew` is generated
# by `gradle wrapper` and replaces this file in your local checkout.
#

set -e

if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi

cat >&2 <<'EOF'
gradlew: no real wrapper jar shipped and no system `gradle` found on PATH.

To bootstrap:

  1. Install Gradle 8.5+ (https://gradle.org/releases/), or
  2. Run `gradle wrapper` once to regenerate the real gradlew + gradle-wrapper.jar, or
  3. Open this project in Android Studio (Iguana | 2023.2.1 or newer) and let
     it auto-generate the wrapper.

After that, `./gradlew tasks` and `./gradlew :app:assembleDebug` will work
normally.
EOF
exit 1

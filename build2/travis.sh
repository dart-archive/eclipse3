#!/bin/sh

# Copyright (c) 2015, the Dart project authors.  Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

# Fast fail the script on failures.
set -e

# Install Dart SDK
if [ "$TRAVIS" ]; then
  export DART_SDK="$PWD/dart-sdk"
  DART_DIST=dartsdk-linux-x64-release.zip
  curl -o $DART_DIST http://storage.googleapis.com/dart-archive/channels/stable/release/latest/sdk/$DART_DIST
  unzip $DART_DIST > /dev/null
  rm $DART_DIST
  export PATH="$DART_SDK/bin":"$PATH"
fi

# Verify SDK installed and display version
dart --version

# Install build dependencies
cd build2
pub get
cd ..

# Run the Dart build
dart --package-root=build2/packages build2/grind.dart build-dart-plugins

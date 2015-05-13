// Copyright (c) 2015, the Dart project authors.  Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import 'dart:async';
import 'dart:io';

import 'package:grinder/grinder.dart';
import 'package:path/path.dart';

main([List<String> args]) {
  grind(args, verifyProjectRoot: false);
}

final Directory rootDir = Directory.current;
final Directory outDir = new Directory(join(rootDir.path, 'out'));

@Task('Build the Dart Plugins')
Future buildDartPlugins(GrinderContext context) async {
  print(rootDir);
  print(outDir);
  return runAsync('ls');
}
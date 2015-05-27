# eclipse3

Eclipse plugins and Dart Editor

[![Build Status](https://travis-ci.org/dart-lang/eclipse3.svg)](https://travis-ci.org/dart-lang/eclipse3)

## Getting and installing the Dart plugin

In Eclipse, choose *Help > Install new software…* and add this URL:

```
http://www.dartlang.org/eclipse/update/channels/stable/
```

## Providing feedback

Please file issues and feedback using the Github issue
[tracker](https://github.com/dart-lang/eclipse3/issues).

## Building

To check it out:

```shell
gclient config git@github.com:dart-lang/eclipse3.git
gclient sync
gclient runhooks
```

To setup your dev workspace:

```shell
cd tools/features/com.google.dart.tools.deploy.feature_releng
java -jar $ANTJAR -f build_rcp.xml -Declipse.home=$E3HOME setupDevWorkspace
```

To build plugins:

```shell
cd tools/features/com.google.dart.eclipse.feature_releng
java -jar $ANTJAR -f build.xml -Declipse.home=$E3HOME
```

To build editor:

```shell
cd tools/features/com.google.dart.tools.deploy.feature_releng
java -jar $ANTJAR -f build_rcp.xml -Declipse.home=$E3HOME
```

* Also see [Build your own Dart Editor with the Eclipse Dart Plugin](http://dartrad.blogspot.com/2015/05/build-your-own-dart-editor.html)

To commit:

```shell
git commit -m 'your message'
git cl upload
<repeat as necessary until lgtm>
git cl land
```

To resync ...

```shell
git pull
```

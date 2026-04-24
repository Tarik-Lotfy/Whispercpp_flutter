# whispercpp_flutter

A Flutter plugin scaffold with platform channels for Android and iOS.

## Structure

- `lib/` exposes the public Dart API.
- `android/` contains the Kotlin platform implementation.
- `ios/` contains the Swift platform implementation.

## Channel

This plugin uses the method channel:

`whispercpp_flutter`

Currently implemented method:

- `getPlatformVersion`

## Usage

```dart
import 'package:whispercpp_flutter/whispercpp_flutter.dart';

final plugin = WhispercppFlutter();
final version = await plugin.getPlatformVersion();
```

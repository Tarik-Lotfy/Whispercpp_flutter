# whispercpp_flutter

A Flutter plugin scaffold with platform channels for Android and iOS.

The current transcription API is set up for multilingual testing, including Arabic,
and the Android plugin now bundles `ggml-tiny.bin` as its default built-in model.

## Structure

- `lib/` exposes the public Dart API.
- `android/` contains the Kotlin platform implementation.
- `ios/` contains the Swift platform implementation.

## Channel

This plugin uses the method channel:

`whispercpp_flutter`

Currently implemented method:

- `transcribe`

## Usage

```dart
import 'package:whispercpp_flutter/whispercpp_flutter.dart';

final controller = WhispercppFlutter();
final text = await controller.transcribe(
  audioPath: '/storage/emulated/0/recordings/sample.wav',
  language: 'auto',
);

final arabicText = await controller.transcribe(
  audioPath: '/storage/emulated/0/recordings/sample.wav',
  language: 'ar',
);

final bundledModelPath = await controller.getBundledTinyModelPath();
```

## Arabic testing

- The Android plugin bundles multilingual `ggml-tiny.bin` by default.
- You can still pass a custom multilingual model path if you want.
- Do not use English-only models such as `ggml-tiny.en.bin` for Arabic.
- Pass `language: 'ar'` to `transcribe(...)`.

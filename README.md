# whispercpp_flutter

A Flutter plugin for on-device speech recognition with Android microphone capture
and Whisper-powered transcription.

The current transcription API is set up for multilingual testing, including Arabic,
and the Android plugin bundles `ggml-medium-q5_0.bin` as its built-in model.

## Structure

- `lib/` exposes the public Dart API.
- `android/` contains the Kotlin platform implementation.
- `ios/` contains the Swift platform implementation.

## Channel

This plugin uses the method channel:

`whispercpp_flutter`

Currently implemented methods:

- `startRecording`
- `stopRecording`
- `stopAndTranscribe`
- `transcribe`
- `getBundledModelPath` (optional `modelFileName`, default `ggml-medium-q5_0.bin`)

## Usage

```dart
import 'package:whispercpp_flutter/whispercpp_flutter.dart';

final controller = WhispercppFlutter();
await controller.startRecording();

final arabicText = await controller.stopAndTranscribe(
  language: 'ar',
);

final bundledModelPath = await controller.getBundledModelPath();
```

## Notes

- Android microphone capture records mono 16 kHz WAV files internally.
- The app still needs runtime microphone permission before `startRecording()`.
- The Android plugin ships multilingual `ggml-medium-q5_0.bin` under `assets/models/`.
- You can still call `transcribe(audioPath: ...)` directly for debugging.
- To use a different bundled file, pass `modelFileName` to `getBundledModelPath` and the same path into `transcribe` / `stopAndTranscribe`.

## Arabic testing

- You can still pass a custom multilingual model path if you want.
- Do not use English-only models such as `ggml-tiny.en.bin` for Arabic.
- Pass `language: 'ar'` to `stopAndTranscribe(...)` or `transcribe(...)`.

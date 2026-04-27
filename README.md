# whispercpp_flutter

A Flutter plugin for on-device speech recognition with microphone capture (Android)
and Whisper-powered transcription via embedded [whisper.cpp](https://github.com/ggml-org/whisper.cpp).

GGML models are **not** bundled with the plugin.
Download a model at runtime with [`downloadModel`](lib/src/download_model.dart) (see [`WhisperModel`](lib/src/whisper_model.dart)), then pass **`modelPath`** into [`stopAndTranscribe`](lib/whispercpp_flutter.dart) (mic capture) or [`transcribeFile`](lib/whispercpp_flutter.dart) (existing WAV).

## Structure

- `lib/` exposes the public Dart API (`WhispercppFlutter`, `downloadModel`, `WhisperModel`).
- `android/` contains the Kotlin platform implementation.
- `ios/` contains a minimal Swift stub (transcription not wired on iOS in this repo yet).
- `android/src/main/cpp/third_party/whisper.cpp` is a **Git submodule** with upstream whisper.cpp (NDK links it into the plugin).

## Clone / collaborators

After cloning this repo, pull submodules so the Android NDK build can find whisper.cpp:

```bash
git submodule update --init --recursive
```

Or clone in one step:

```bash
git clone --recurse-submodules <repository-url>
```

## Channel

Method channel name: `whispercpp_flutter`

Implemented methods:

- `startRecording` — begin microphone capture to a WAV path.
- **`stopAndTranscribe`** — stops capture **and** runs Whisper on that WAV (single native hop). There is no separate stop-without-transcribe API.
- **`transcribeFile`** — Whisper on an arbitrary WAV path (**`modelPath`** + **`audioPath`**).

## Usage

Mic workflow (preferred):

```dart
import 'package:whispercpp_flutter/whispercpp_flutter.dart';

final whisper = WhispercppFlutter();

final modelPath = await downloadModel(
  model: WhisperModel.mediumQ5,
);

await whisper.startRecording();
final text = await whisper.stopAndTranscribe(
  modelPath: modelPath,
  language: 'ar',
);
```

Existing file on disk:

```dart
final text = await whisper.transcribeFile(
  modelPath: modelPath,
  audioPath: '/path/to/file.wav',
);
```

Optional mirror / custom host (no trailing slash):

```dart
await downloadModel(
  model: WhisperModel.mediumQ5,
  downloadHost: 'https://hf-mirror.com/ggerganov/whisper.cpp/resolve/main',
);
```

Default download location when `destinationDir` is omitted: application support directory + `whisper_models/` (via `path_provider`).

## Notes

- Android records mono 16 kHz WAV files internally.
- Apps need **microphone** permission before `startRecording()`.
- Models come from Hugging Face (default `https://huggingface.co/ggerganov/whisper.cpp/resolve/main`). Ensure filenames match [`WhisperModel`](lib/src/whisper_model.dart) or pass a compatible URL stem when hosting your own mirror.
- Do not use English-only models such as `ggml-tiny.en.bin` for Arabic; pass `language: 'ar'` for Arabic.

## Arabic testing

- Use a multilingual variant (e.g. `WhisperModel.smallQ5` or `WhisperModel.smallQ8`).
- Pass `language: 'ar'` to `stopAndTranscribe` / `transcribeFile`.

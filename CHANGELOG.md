# Changelog

## 0.1.0

- **Breaking:** Removed **`stopRecording`** from the public API and Android channel; stopping the mic only happens inside **`stopAndTranscribe`**.
- **Breaking:** Removed bundled GGML assets from the plugin; models are downloaded at runtime via `downloadModel` + `WhisperModel` (Hugging Face `ggerganov/whisper.cpp` by default).
- **Breaking:** **`transcribe`** renamed to **`transcribeFile`** (arbitrary WAV on disk). Primary mic API is **`stopAndTranscribe`** (`modelPath` + `language`); Android channel **`transcribe`** renamed to **`transcribeFile`**.
- Removed `getBundledModelPath` from the Dart API and Android implementation.
- Android: validates `modelPath` / `audioPath` exist before native inference.

## 0.0.1

- Initial `whispercpp_flutter` Flutter plugin scaffold with platform channels for Android and iOS.

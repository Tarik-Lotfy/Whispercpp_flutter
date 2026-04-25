# Whisper Flutter Plugin Instructions

## Goal
Build a Flutter plugin that integrates `whisper.cpp` for on-device speech-to-text.

## Architecture
Flutter (Dart)  
-> Platform Channels  
-> Native (Kotlin / Swift)  
-> C++ (`whisper.cpp`)

## Current Status
- Plugin template created
- Platform channel works
- Native C++ function call works (hello world)

## Next Steps
1. Replace hello world with real whisper inference
2. Load GGML model from device storage
3. Accept audio file path as input
4. Return transcription string to Flutter

## Constraints
- Must work offline
- Must support multilingual (Arabic included)
- Start with tiny model for testing
- Avoid streaming for now (batch only)

## Future Goals
- Add microphone support
- Add streaming transcription
- Optimize performance

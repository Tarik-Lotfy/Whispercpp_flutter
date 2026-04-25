# Architecture

- Dart layer: public API
- Native layer: platform bridge
- C++ layer: whisper inference

Data Flow:
audio file → native → C++ → text → Flutter

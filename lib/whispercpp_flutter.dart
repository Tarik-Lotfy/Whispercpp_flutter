import 'src/platform_interface.dart';

export 'src/download_model.dart'
    show
        WhisperModelDownloadException,
        DownloadProgressCallback,
        downloadModel,
        whisperDownloadHost;
export 'src/whisper_model.dart' show WhisperModel;

abstract final class WhisperLanguages {
  static const String auto = 'auto';
  static const String arabic = 'ar';
  static const String english = 'en';
}

class WhispercppFlutter {
  Future<String?> startRecording() {
    return WhispercppFlutterPlatform.instance.startRecording();
  }

  /// Stops active recording and transcribes that WAV in one step (native).
  Future<String?> stopAndTranscribe({
    required String modelPath,
    String language = WhisperLanguages.auto,
  }) {
    return WhispercppFlutterPlatform.instance.stopAndTranscribe(
      modelPath: modelPath,
      language: language,
    );
  }

  /// Run Whisper on an arbitrary WAV path (e.g. imported file — not the mic capture helper above).
  Future<String?> transcribeFile({
    required String modelPath,
    required String audioPath,
    String language = WhisperLanguages.auto,
  }) {
    return WhispercppFlutterPlatform.instance.transcribeFile(
      modelPath: modelPath,
      audioPath: audioPath,
      language: language,
    );
  }
}

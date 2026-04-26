import 'src/platform_interface.dart';

abstract final class WhisperLanguages {
  static const String auto = 'auto';
  static const String arabic = 'ar';
  static const String english = 'en';
}

class WhispercppFlutter {
  Future<String?> startRecording() {
    return WhispercppFlutterPlatform.instance.startRecording();
  }

  Future<String?> stopRecording() {
    return WhispercppFlutterPlatform.instance.stopRecording();
  }

  Future<String?> stopAndTranscribe({
    String? modelPath,
    String language = WhisperLanguages.auto,
  }) {
    return WhispercppFlutterPlatform.instance.stopAndTranscribe(
      modelPath: modelPath,
      language: language,
    );
  }

  Future<String?> transcribe({
    String? modelPath,
    required String audioPath,
    String language = WhisperLanguages.auto,
  }) {
    return WhispercppFlutterPlatform.instance.transcribe(
      modelPath: modelPath,
      audioPath: audioPath,
      language: language,
    );
  }

  Future<String?> getBundledModelPath({
    String modelFileName = bundeledWhisperModelName,
  }) {
    return WhispercppFlutterPlatform.instance.getBundledModelPath(
      modelFileName: modelFileName,
    );
  }
}

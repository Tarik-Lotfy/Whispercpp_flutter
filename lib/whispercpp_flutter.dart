import 'src/platform_interface.dart';

abstract final class WhisperLanguages {
  static const String auto = 'auto';
  static const String arabic = 'ar';
  static const String english = 'en';
}

class WhispercppFlutter {
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

  Future<String?> getBundledTinyModelPath() {
    return WhispercppFlutterPlatform.instance.getBundledTinyModelPath();
  }
}

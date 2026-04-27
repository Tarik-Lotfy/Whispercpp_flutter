import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'method_channel.dart';

abstract class WhispercppFlutterPlatform extends PlatformInterface {
  WhispercppFlutterPlatform() : super(token: _token);

  static final Object _token = Object();

  static WhispercppFlutterPlatform _instance = MethodChannelWhispercppFlutter();

  static WhispercppFlutterPlatform get instance => _instance;

  static set instance(WhispercppFlutterPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> startRecording() {
    throw UnimplementedError('startRecording() has not been implemented.');
  }

  /// Stops active recording and runs Whisper on the captured WAV (one native hop).
  Future<String?> stopAndTranscribe({
    required String modelPath,
    String language = 'auto',
  }) {
    throw UnimplementedError('stopAndTranscribe() has not been implemented.');
  }

  /// Transcribe an existing WAV on disk (was not captured by this plugin session).
  Future<String?> transcribeFile({
    required String modelPath,
    required String audioPath,
    String language = 'auto',
  }) {
    throw UnimplementedError('transcribeFile() has not been implemented.');
  }
}

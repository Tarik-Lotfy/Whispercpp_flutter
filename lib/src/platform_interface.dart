import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'method_channel.dart';

const String bundeledWhisperModelName = 'ggml-medium-q5_0.bin';

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

  Future<String?> stopRecording() {
    throw UnimplementedError('stopRecording() has not been implemented.');
  }

  Future<String?> stopAndTranscribe({
    String? modelPath,
    String language = 'auto',
  }) {
    throw UnimplementedError('stopAndTranscribe() has not been implemented.');
  }

  Future<String?> transcribe({
    String? modelPath,
    required String audioPath,
    String language = 'auto',
  }) {
    throw UnimplementedError('transcribe() has not been implemented.');
  }

  Future<String?> getBundledModelPath({
    String modelFileName = bundeledWhisperModelName,
  }) {
    throw UnimplementedError(
      'getBundledModelPath() has not been implemented.',
    );
  }
}

import 'package:flutter_test/flutter_test.dart';
import 'package:whispercpp_flutter/src/platform_interface.dart';
import 'package:whispercpp_flutter/whispercpp_flutter.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockWhispercppFlutterPlatform
    with MockPlatformInterfaceMixin
    implements WhispercppFlutterPlatform {
  @override
  Future<String?> startRecording() async => '/tmp/recording.wav';

  @override
  Future<String?> stopAndTranscribe({
    required String modelPath,
    String language = 'auto',
  }) async {
    return 'stop-and-transcribe:$language:$modelPath';
  }

  @override
  Future<String?> transcribeFile({
    required String modelPath,
    required String audioPath,
    String language = 'auto',
  }) async {
    return 'file:$language:$modelPath:$audioPath';
  }
}

void main() {
  test('startRecording delegates to platform interface', () async {
    final plugin = WhispercppFlutter();
    final fakePlatform = MockWhispercppFlutterPlatform();
    WhispercppFlutterPlatform.instance = fakePlatform;

    expect(await plugin.startRecording(), '/tmp/recording.wav');
  });

  test('stopAndTranscribe delegates to platform interface', () async {
    final plugin = WhispercppFlutter();
    final fakePlatform = MockWhispercppFlutterPlatform();
    WhispercppFlutterPlatform.instance = fakePlatform;

    expect(
      await plugin.stopAndTranscribe(
        modelPath: '/models/ggml-medium-q5_0.bin',
        language: 'ar',
      ),
      'stop-and-transcribe:ar:/models/ggml-medium-q5_0.bin',
    );
  });

  test('transcribeFile delegates to platform interface', () async {
    final plugin = WhispercppFlutter();
    final fakePlatform = MockWhispercppFlutterPlatform();
    WhispercppFlutterPlatform.instance = fakePlatform;

    expect(
      await plugin.transcribeFile(
        modelPath: '/models/ggml-medium-q5_0.bin',
        audioPath: '/audio/sample.wav',
        language: 'ar',
      ),
      'file:ar:/models/ggml-medium-q5_0.bin:/audio/sample.wav',
    );
  });

  test('WhisperModel maps to HF ggml filename', () {
    expect(WhisperModel.mediumQ5.fileName, 'ggml-medium-q5_0.bin');
    expect(WhisperModel.tinyQ8.fileName, 'ggml-tiny-q8_0.bin');
    expect(WhisperModel.baseQ5.fileName, 'ggml-base-q5_1.bin');
    expect(
        WhisperModel.largeV3TurboQ5.fileName, 'ggml-large-v3-turbo-q5_0.bin');
  });
}

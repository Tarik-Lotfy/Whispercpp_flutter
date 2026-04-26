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
  Future<String?> stopRecording() async => '/tmp/recording.wav';

  @override
  Future<String?> stopAndTranscribe({
    String? modelPath,
    String language = 'auto',
  }) async {
    return 'stop:$language:${modelPath ?? 'bundled-default'}:/tmp/recording.wav';
  }

  @override
  Future<String?> transcribe({
    String? modelPath,
    required String audioPath,
    String language = 'auto',
  }) async {
    return 'stub:$language:${modelPath ?? 'bundled-default'}:$audioPath';
  }

  @override
  Future<String?> getBundledModelPath({
    String modelFileName = bundeledWhisperModelName,
  }) async =>
      '/models/bundled/$modelFileName';
}

void main() {
  test('startRecording delegates to platform interface', () async {
    final plugin = WhispercppFlutter();
    final fakePlatform = MockWhispercppFlutterPlatform();
    WhispercppFlutterPlatform.instance = fakePlatform;

    expect(await plugin.startRecording(), '/tmp/recording.wav');
  });

  test('stopRecording delegates to platform interface', () async {
    final plugin = WhispercppFlutter();
    final fakePlatform = MockWhispercppFlutterPlatform();
    WhispercppFlutterPlatform.instance = fakePlatform;

    expect(await plugin.stopRecording(), '/tmp/recording.wav');
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
      'stop:ar:/models/ggml-medium-q5_0.bin:/tmp/recording.wav',
    );
  });

  test('transcribe delegates to platform interface', () async {
    final plugin = WhispercppFlutter();
    final fakePlatform = MockWhispercppFlutterPlatform();
    WhispercppFlutterPlatform.instance = fakePlatform;

    expect(
      await plugin.transcribe(
        modelPath: '/models/ggml-medium-q5_0.bin',
        audioPath: '/audio/sample.wav',
        language: 'ar',
      ),
      'stub:ar:/models/ggml-medium-q5_0.bin:/audio/sample.wav',
    );
  });

  test('transcribe uses the bundled default model when modelPath is null',
      () async {
    final plugin = WhispercppFlutter();
    final fakePlatform = MockWhispercppFlutterPlatform();
    WhispercppFlutterPlatform.instance = fakePlatform;

    expect(
      await plugin.transcribe(audioPath: '/audio/sample.wav'),
      'stub:auto:bundled-default:/audio/sample.wav',
    );
  });

  test('getBundledModelPath delegates to platform interface', () async {
    final plugin = WhispercppFlutter();
    final fakePlatform = MockWhispercppFlutterPlatform();
    WhispercppFlutterPlatform.instance = fakePlatform;

    expect(
      await plugin.getBundledModelPath(),
      '/models/bundled/ggml-medium-q5_0.bin',
    );
    expect(
      await plugin.getBundledModelPath(modelFileName: 'custom.bin'),
      '/models/bundled/custom.bin',
    );
  });
}

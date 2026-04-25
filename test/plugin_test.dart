import 'package:flutter_test/flutter_test.dart';
import 'package:whispercpp_flutter/src/platform_interface.dart';
import 'package:whispercpp_flutter/whispercpp_flutter.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockWhispercppFlutterPlatform
    with MockPlatformInterfaceMixin
    implements WhispercppFlutterPlatform {
  @override
  Future<String?> transcribe({
    String? modelPath,
    required String audioPath,
    String language = 'auto',
  }) async {
    return 'stub:$language:${modelPath ?? 'bundled-tiny'}:$audioPath';
  }

  @override
  Future<String?> getBundledTinyModelPath() async =>
      '/models/bundled/ggml-tiny.bin';
}

void main() {
  test('transcribe delegates to platform interface', () async {
    final plugin = WhispercppFlutter();
    final fakePlatform = MockWhispercppFlutterPlatform();
    WhispercppFlutterPlatform.instance = fakePlatform;

    expect(
      await plugin.transcribe(
        modelPath: '/models/ggml-tiny.bin',
        audioPath: '/audio/sample.wav',
        language: 'ar',
      ),
      'stub:ar:/models/ggml-tiny.bin:/audio/sample.wav',
    );
  });

  test('transcribe uses the bundled tiny model by default', () async {
    final plugin = WhispercppFlutter();
    final fakePlatform = MockWhispercppFlutterPlatform();
    WhispercppFlutterPlatform.instance = fakePlatform;

    expect(
      await plugin.transcribe(audioPath: '/audio/sample.wav'),
      'stub:auto:bundled-tiny:/audio/sample.wav',
    );
  });

  test('getBundledTinyModelPath delegates to platform interface', () async {
    final plugin = WhispercppFlutter();
    final fakePlatform = MockWhispercppFlutterPlatform();
    WhispercppFlutterPlatform.instance = fakePlatform;

    expect(
      await plugin.getBundledTinyModelPath(),
      '/models/bundled/ggml-tiny.bin',
    );
  });
}

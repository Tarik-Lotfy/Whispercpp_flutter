import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'platform_interface.dart';

class MethodChannelWhispercppFlutter extends WhispercppFlutterPlatform {
  @visibleForTesting
  final methodChannel = const MethodChannel('whispercpp_flutter');

  @override
  Future<String?> startRecording() {
    return methodChannel.invokeMethod<String>('startRecording');
  }

  @override
  Future<String?> stopAndTranscribe({
    required String modelPath,
    String language = 'auto',
  }) {
    return methodChannel.invokeMethod<String>(
      'stopAndTranscribe',
      <String, Object?>{
        'modelPath': modelPath,
        'language': language,
      },
    );
  }

  @override
  Future<String?> transcribeFile({
    required String modelPath,
    required String audioPath,
    String language = 'auto',
  }) {
    return methodChannel.invokeMethod<String>(
      'transcribeFile',
      <String, Object?>{
        'modelPath': modelPath,
        'audioPath': audioPath,
        'language': language,
      },
    );
  }
}

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'platform_interface.dart';

class MethodChannelWhispercppFlutter extends WhispercppFlutterPlatform {
  @visibleForTesting
  final methodChannel = const MethodChannel('whispercpp_flutter');

  @override
  Future<String?> transcribe({
    String? modelPath,
    required String audioPath,
    String language = 'auto',
  }) {
    return methodChannel.invokeMethod<String>(
      'transcribe',
      <String, Object?>{
        'modelPath': modelPath,
        'audioPath': audioPath,
        'language': language,
      },
    );
  }

  @override
  Future<String?> getBundledTinyModelPath() {
    return methodChannel.invokeMethod<String>('getBundledTinyModelPath');
  }
}

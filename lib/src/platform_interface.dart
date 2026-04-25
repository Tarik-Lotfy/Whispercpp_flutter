import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'method_channel.dart';

abstract class WhispercppFlutterPlatform extends PlatformInterface {
  WhispercppFlutterPlatform() : super(token: _token);

  static final Object _token = Object();

  static WhispercppFlutterPlatform _instance =
      MethodChannelWhispercppFlutter();

  static WhispercppFlutterPlatform get instance => _instance;

  static set instance(WhispercppFlutterPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> transcribe({
    String? modelPath,
    required String audioPath,
    String language = 'auto',
  }) {
    throw UnimplementedError('transcribe() has not been implemented.');
  }

  Future<String?> getBundledTinyModelPath() {
    throw UnimplementedError(
      'getBundledTinyModelPath() has not been implemented.',
    );
  }
}

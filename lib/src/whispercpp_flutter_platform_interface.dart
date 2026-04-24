import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'method_channel_whispercpp_flutter.dart';

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

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('getPlatformVersion() has not been implemented.');
  }
}

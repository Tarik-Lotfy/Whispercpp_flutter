import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'whispercpp_flutter_platform_interface.dart';

class MethodChannelWhispercppFlutter extends WhispercppFlutterPlatform {
  @visibleForTesting
  final methodChannel = const MethodChannel('whispercpp_flutter');

  @override
  Future<String?> getPlatformVersion() {
    return methodChannel.invokeMethod<String>('getPlatformVersion');
  }
}

import 'src/whispercpp_flutter_platform_interface.dart';

class WhispercppFlutter {
  Future<String?> getPlatformVersion() {
    return WhispercppFlutterPlatform.instance.getPlatformVersion();
  }
}

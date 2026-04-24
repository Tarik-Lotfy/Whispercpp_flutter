import 'package:flutter_test/flutter_test.dart';
import 'package:whispercpp_flutter/src/whispercpp_flutter_platform_interface.dart';
import 'package:whispercpp_flutter/whispercpp_flutter.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockWhispercppFlutterPlatform
    with MockPlatformInterfaceMixin
    implements WhispercppFlutterPlatform {
  @override
  Future<String?> getPlatformVersion() async => '42';
}

void main() {
  test('getPlatformVersion delegates to platform interface', () async {
    final plugin = WhispercppFlutter();
    final fakePlatform = MockWhispercppFlutterPlatform();
    WhispercppFlutterPlatform.instance = fakePlatform;

    expect(await plugin.getPlatformVersion(), '42');
  });
}

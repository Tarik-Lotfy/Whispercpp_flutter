import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:path/path.dart' as p;
import 'package:whispercpp_flutter/whispercpp_flutter.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('downloadModel skips HTTP when file already exists', () async {
    final tmp = Directory.systemTemp.createTempSync('whisper_dl_test');
    try {
      final dest = tmp.path;
      final path =
          p.join(dest, WhisperModel.tinyQ8.fileName);
      File(path).writeAsBytesSync(List<int>.filled(1024 * 101, 0));

      final again = await downloadModel(
        model: WhisperModel.tinyQ8,
        destinationDir: dest,
        downloadHost: 'https://invalid.example.invalid/no-network',
      );

      expect(again, path);
      expect(File(again).existsSync(), isTrue);
    } finally {
      tmp.deleteSync(recursive: true);
    }
  });
}

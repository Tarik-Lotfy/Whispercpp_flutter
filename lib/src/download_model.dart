import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

import 'whisper_model.dart';

/// Default Hugging Face folder URL for whisper.cpp GGML binaries (no trailing slash).
const String whisperDownloadHost =
    'https://huggingface.co/ggerganov/whisper.cpp/resolve/main';

/// Progress: [0,1] when total size known; [downloadedMb] / [totalMb] in megabytes.
typedef DownloadProgressCallback = void Function(
  double progress,
  double downloadedMb,
  double totalMb,
);

/// Thrown when the download fails or the HTTP response is not a valid model file.
class WhisperModelDownloadException implements Exception {
  WhisperModelDownloadException(this.message);
  final String message;

  @override
  String toString() => 'WhisperModelDownloadException: $message';
}

/// Downloads `ggml-{model.modelName}.bin` into [destinationDir].
///
/// When [destinationDir] is null, uses application support dir +
/// `whisper_models`.
///
/// [downloadHost] is the base URL without trailing slash (default:
/// [whisperDownloadHost]); the file is fetched from
/// `{downloadHost}/ggml-{model.modelName}.bin`.
///
/// Writes via a `.part` temp file and renames when complete.
Future<String> downloadModel({
  required WhisperModel model,
  String? destinationDir,
  String? downloadHost,
  DownloadProgressCallback? onProgress,
}) async {
  final host = (downloadHost == null || downloadHost.isEmpty)
      ? whisperDownloadHost
      : downloadHost.replaceAll(RegExp(r'/+$'), '');
  final uri = Uri.parse('$host/${model.fileName}');

  final dirPath = destinationDir ??
      p.join((await getApplicationSupportDirectory()).path, 'whisper_models');
  final dir = Directory(dirPath);
  if (!await dir.exists()) {
    await dir.create(recursive: true);
  }

  final outFile = File(p.join(dirPath, model.fileName));
  final partFile = File('${outFile.path}.part');

  if (await outFile.exists()) {
    return outFile.path;
  }

  final httpClient = HttpClient();
  try {
    final request = await httpClient.getUrl(uri);
    final response = await request.close();

    if (response.statusCode != 200) {
      throw WhisperModelDownloadException(
        'HTTP ${response.statusCode} for $uri',
      );
    }

    final total = response.contentLength;
    var received = 0;

    IOSink sink;
    try {
      sink = partFile.openWrite();
    } catch (e, st) {
      debugPrint('downloadModel openWrite: $e\n$st');
      rethrow;
    }

    try {
      await for (final chunk in response) {
        received += chunk.length;
        sink.add(chunk);
        if (onProgress != null && total > 0) {
          onProgress(
            received / total,
            received / (1024 * 1024),
            total / (1024 * 1024),
          );
        }
      }
      await sink.flush();
      await sink.close();
    } catch (e) {
      await sink.close();
      if (await partFile.exists()) {
        await partFile.delete();
      }
      rethrow;
    }

    if (total > 0 && received != total) {
      if (await partFile.exists()) await partFile.delete();
      throw WhisperModelDownloadException(
        'Incomplete download: got $received bytes, expected $total',
      );
    }

    if (received < 1024 * 100) {
      if (await partFile.exists()) await partFile.delete();
      throw WhisperModelDownloadException(
        'Download too small ($received bytes); likely not a model file.',
      );
    }

    await partFile.rename(outFile.path);
    return outFile.path;
  } finally {
    httpClient.close(force: true);
  }
}

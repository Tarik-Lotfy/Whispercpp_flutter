import 'dart:async';
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

/// Same minimum as post-download validation; existing files below this are treated as stale.
const int _kMinModelBytes = 100 * 1024;

final Map<String, Future<String>> _downloadsInFlight =
    <String, Future<String>>{};

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
///
/// Concurrent calls for the same destination file share one download; the
/// response stream is piped with [IOSink.addStream] for backpressure.
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
  final key = p.normalize(outFile.path);

  return _downloadsInFlight.putIfAbsent(key, () {
    final future = _downloadModelOnce(
      uri: uri,
      outFile: outFile,
      onProgress: onProgress,
    );
    future.whenComplete(() => _downloadsInFlight.remove(key));
    return future;
  });
}

Future<bool> _isPlausibleModelFile(File f) async {
  try {
    if (!await f.exists()) return false;
    final len = await f.length();
    return len >= _kMinModelBytes;
  } catch (_) {
    return false;
  }
}

Future<void> _deleteIfExists(File f) async {
  try {
    if (await f.exists()) await f.delete();
  } catch (e, st) {
    debugPrint('downloadModel delete stale file: $e\n$st');
  }
}

Future<String> _downloadModelOnce({
  required Uri uri,
  required File outFile,
  DownloadProgressCallback? onProgress,
}) async {
  final partFile = File('${outFile.path}.part');

  if (await _isPlausibleModelFile(outFile)) {
    return outFile.path;
  }

  await _deleteIfExists(outFile);
  await _deleteIfExists(partFile);

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
      await sink.addStream(
        response.map((chunk) {
          received += chunk.length;
          if (onProgress != null && total > 0) {
            onProgress(
              received / total,
              received / (1024 * 1024),
              total / (1024 * 1024),
            );
          }
          return chunk;
        }),
      );
      await sink.close();
    } catch (e) {
      await sink.close().catchError((_) {});
      await _deleteIfExists(partFile);
      rethrow;
    }

    if (total > 0 && received != total) {
      await _deleteIfExists(partFile);
      throw WhisperModelDownloadException(
        'Incomplete download: got $received bytes, expected $total',
      );
    }

    if (received < _kMinModelBytes) {
      await _deleteIfExists(partFile);
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

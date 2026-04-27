/// GGML Whisper models hosted under
/// `https://huggingface.co/ggerganov/whisper.cpp/tree/main`.
///
/// Each value maps to `ggml-{modelName}.bin` on the default download host.
///
/// Only **q8_0** and **q5** (q5_0 / q5_1 — whichever exists upstream) variants are
/// listed. HF uses **q5_1** for tiny/base/small and **q5_0** for medium/large-v2/v3/turbo.
enum WhisperModel {
  // --- Tiny (multilingual) ---
  tinyQ8('tiny-q8_0'),
  tinyQ5('tiny-q5_1'),

  // --- Tiny English-only ---
  tinyEnQ8('tiny.en-q8_0'),
  tinyEnQ5('tiny.en-q5_1'),

  // --- Base (multilingual) ---
  baseQ8('base-q8_0'),
  baseQ5('base-q5_1'),

  // --- Base English-only ---
  baseEnQ8('base.en-q8_0'),
  baseEnQ5('base.en-q5_1'),

  // --- Small (multilingual) ---
  smallQ8('small-q8_0'),
  smallQ5('small-q5_1'),

  // --- Small English-only ---
  smallEnQ8('small.en-q8_0'),
  smallEnQ5('small.en-q5_1'),

  // --- Medium (multilingual) ---
  mediumQ8('medium-q8_0'),
  mediumQ5('medium-q5_0'),

  // --- Medium English-only ---
  mediumEnQ8('medium.en-q8_0'),
  mediumEnQ5('medium.en-q5_0'),

  // --- Large v2 (multilingual) ---
  largeV2Q8('large-v2-q8_0'),
  largeV2Q5('large-v2-q5_0'),

  /// Quantized large-v3 (HF ships **q5_0** for this tier).
  largeV3Q5('large-v3-q5_0'),

  // --- Large v3 turbo (multilingual) ---
  largeV3TurboQ8('large-v3-turbo-q8_0'),
  largeV3TurboQ5('large-v3-turbo-q5_0');

  const WhisperModel(this.modelName);

  /// Stem used in Hugging Face filenames: `ggml-{modelName}.bin`.
  final String modelName;

  /// Expected filename on disk after download.
  String get fileName => 'ggml-$modelName.bin';
}

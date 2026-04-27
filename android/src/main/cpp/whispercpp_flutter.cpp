#include <jni.h>

#include <algorithm>
#include <cstdint>
#include <fstream>
#include <sstream>
#include <string>
#include <thread>
#include <vector>

#include "whisper.h"

namespace {

constexpr int kExpectedSampleRate = 16000;
constexpr int kExpectedChannels = 1;
constexpr int kExpectedBitsPerSample = 16;

struct WavData {
  int sample_rate = 0;
  int channels = 0;
  int bits_per_sample = 0;
  std::vector<float> samples;
};

std::string JStringToStdString(JNIEnv* env, jstring value) {
  if (value == nullptr) {
    return "";
  }

  const char* chars = env->GetStringUTFChars(value, nullptr);
  if (chars == nullptr) {
    return "";
  }

  std::string result(chars);
  env->ReleaseStringUTFChars(value, chars);
  return result;
}

[[noreturn]] void ThrowIllegalArgument(JNIEnv* env, const char* message) {
  jclass illegal_argument = env->FindClass("java/lang/IllegalArgumentException");
  env->ThrowNew(illegal_argument, message);
  throw 0;
}

uint16_t ReadLe16(std::istream& input) {
  uint8_t bytes[2];
  input.read(reinterpret_cast<char*>(bytes), sizeof(bytes));
  if (!input) {
    throw std::runtime_error("Unexpected end of WAV file.");
  }
  return static_cast<uint16_t>(bytes[0] | (bytes[1] << 8));
}

uint32_t ReadLe32(std::istream& input) {
  uint8_t bytes[4];
  input.read(reinterpret_cast<char*>(bytes), sizeof(bytes));
  if (!input) {
    throw std::runtime_error("Unexpected end of WAV file.");
  }
  return static_cast<uint32_t>(bytes[0] | (bytes[1] << 8) | (bytes[2] << 16) |
                               (bytes[3] << 24));
}

void SkipBytes(std::istream& input, std::streamoff count) {
  input.seekg(count, std::ios::cur);
  if (!input) {
    throw std::runtime_error("Failed to skip WAV bytes.");
  }
}

WavData ReadPcm16MonoWav(const std::string& audio_path) {
  std::ifstream input(audio_path, std::ios::binary);
  if (!input.is_open()) {
    throw std::runtime_error("Audio file could not be opened.");
  }

  char riff[4];
  input.read(riff, sizeof(riff));
  if (std::string(riff, sizeof(riff)) != "RIFF") {
    throw std::runtime_error("Audio file is not a RIFF WAV file.");
  }

  (void)ReadLe32(input);  // Chunk size

  char wave[4];
  input.read(wave, sizeof(wave));
  if (std::string(wave, sizeof(wave)) != "WAVE") {
    throw std::runtime_error("Audio file is not a WAVE file.");
  }

  bool found_fmt = false;
  bool found_data = false;
  uint16_t audio_format = 0;
  WavData wav;
  std::vector<int16_t> pcm16;

  while (input && (!found_fmt || !found_data)) {
    char chunk_id[4];
    input.read(chunk_id, sizeof(chunk_id));
    if (!input) {
      break;
    }

    const uint32_t chunk_size = ReadLe32(input);
    const std::string id(chunk_id, sizeof(chunk_id));

    if (id == "fmt ") {
      audio_format = ReadLe16(input);
      wav.channels = static_cast<int>(ReadLe16(input));
      wav.sample_rate = static_cast<int>(ReadLe32(input));
      (void)ReadLe32(input);  // byte rate
      (void)ReadLe16(input);  // block align
      wav.bits_per_sample = static_cast<int>(ReadLe16(input));

      if (chunk_size > 16) {
        SkipBytes(input, chunk_size - 16);
      }

      found_fmt = true;
    } else if (id == "data") {
      if (chunk_size % 2 != 0) {
        throw std::runtime_error("WAV data chunk is not PCM16 aligned.");
      }

      pcm16.resize(chunk_size / 2);
      input.read(reinterpret_cast<char*>(pcm16.data()),
                 static_cast<std::streamsize>(chunk_size));
      if (!input) {
        throw std::runtime_error("Failed to read WAV PCM data.");
      }
      found_data = true;
    } else {
      SkipBytes(input, chunk_size);
    }
  }

  if (!found_fmt || !found_data) {
    throw std::runtime_error("WAV file is missing format or data chunk.");
  }

  if (audio_format != 1) {
    throw std::runtime_error("Only PCM WAV files are supported.");
  }
  if (wav.channels != kExpectedChannels) {
    throw std::runtime_error("Only mono WAV files are supported.");
  }
  if (wav.sample_rate != kExpectedSampleRate) {
    throw std::runtime_error("Only 16 kHz WAV files are supported.");
  }
  if (wav.bits_per_sample != kExpectedBitsPerSample) {
    throw std::runtime_error("Only PCM16 WAV files are supported.");
  }

  wav.samples.reserve(pcm16.size());
  for (int16_t sample : pcm16) {
    wav.samples.push_back(
        std::clamp(static_cast<float>(sample) / 32768.0f, -1.0f, 1.0f));
  }

  return wav;
}

std::string RunWhisperInference(const std::string& model_path,
                                const std::string& audio_path,
                                const std::string& language) {
  whisper_context_params cparams = whisper_context_default_params();
  whisper_context* context =
      whisper_init_from_file_with_params(model_path.c_str(), cparams);
  if (context == nullptr) {
    throw std::runtime_error("Failed to load Whisper model.");
  }

  try {
    const WavData wav = ReadPcm16MonoWav(audio_path);

    whisper_full_params params =
        whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.no_context = true;
    params.single_segment = false;
    params.no_timestamps = true;
    {
      unsigned hc = std::thread::hardware_concurrency();
      if (hc == 0) hc = 4;
      params.n_threads = static_cast<int>(std::min(hc, 8u));
    }

    if (language.empty() || language == "auto") {
      params.language = nullptr;
      params.detect_language = true;
    } else {
      params.language = language.c_str();
      params.detect_language = false;
    }

    if (whisper_full(context, params, wav.samples.data(),
                     static_cast<int>(wav.samples.size())) != 0) {
      throw std::runtime_error("whisper_full() failed.");
    }

    const int segment_count = whisper_full_n_segments(context);
    std::ostringstream text;
    for (int i = 0; i < segment_count; ++i) {
      const char* segment = whisper_full_get_segment_text(context, i);
      if (segment == nullptr) {
        continue;
      }
      if (text.tellp() > 0) {
        text << ' ';
      }
      text << segment;
    }

    whisper_free(context);
    return text.str();
  } catch (...) {
    whisper_free(context);
    throw;
  }
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_whispercpp_1flutter_WhisperPlugin_transcribeNative(
    JNIEnv* env,
    jobject /* this */,
    jstring model_path,
    jstring audio_path,
    jstring language) {
  try {
    const std::string modelPath = JStringToStdString(env, model_path);
    const std::string audioPath = JStringToStdString(env, audio_path);
    const std::string requestedLanguage = JStringToStdString(env, language);

    if (modelPath.empty()) {
      ThrowIllegalArgument(env, "modelPath must not be empty.");
    }
    if (audioPath.empty()) {
      ThrowIllegalArgument(env, "audioPath must not be empty.");
    }

    const std::string text =
        RunWhisperInference(modelPath, audioPath, requestedLanguage);
    return env->NewStringUTF(text.c_str());
  } catch (const std::runtime_error& error) {
    jclass illegal_argument = env->FindClass("java/lang/IllegalArgumentException");
    env->ThrowNew(illegal_argument, error.what());
    return nullptr;
  } catch (...) {
    return nullptr;
  }
}

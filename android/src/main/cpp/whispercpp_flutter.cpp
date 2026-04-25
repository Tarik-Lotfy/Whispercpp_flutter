#include <jni.h>
#include <fstream>
#include <sstream>
#include <string>

namespace {

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

bool FileExists(const std::string& path) {
  std::ifstream file(path);
  return file.good();
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_whispercpp_1flutter_WhisperPlugin_transcribeNative(
    JNIEnv* env,
    jobject /* this */,
    jstring model_path,
    jstring audio_path,
    jstring language) {
  const std::string modelPath = JStringToStdString(env, model_path);
  const std::string audioPath = JStringToStdString(env, audio_path);
  const std::string requestedLanguage = JStringToStdString(env, language);

  if (modelPath.empty()) {
    jclass illegalArgument = env->FindClass("java/lang/IllegalArgumentException");
    env->ThrowNew(illegalArgument, "modelPath must not be empty.");
    return nullptr;
  }

  if (audioPath.empty()) {
    jclass illegalArgument = env->FindClass("java/lang/IllegalArgumentException");
    env->ThrowNew(illegalArgument, "audioPath must not be empty.");
    return nullptr;
  }

  if (!FileExists(modelPath)) {
    jclass illegalArgument = env->FindClass("java/lang/IllegalArgumentException");
    env->ThrowNew(illegalArgument, "Model file could not be opened.");
    return nullptr;
  }

  if (!FileExists(audioPath)) {
    jclass illegalArgument = env->FindClass("java/lang/IllegalArgumentException");
    env->ThrowNew(illegalArgument, "Audio file could not be opened.");
    return nullptr;
  }

  std::ostringstream response;
  response << "Transcription pipeline wired. Replace this stub with whisper.cpp "
              "inference using model="
           << modelPath << ", audio=" << audioPath << ", language="
           << (requestedLanguage.empty() ? "auto" : requestedLanguage);

  return env->NewStringUTF(response.str().c_str());
}

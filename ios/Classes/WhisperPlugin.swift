import Flutter

public class WhisperPlugin: NSObject, FlutterPlugin {
  public static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(name: "whispercpp_flutter", binaryMessenger: registrar.messenger())
    let instance = WhisperPlugin()
    registrar.addMethodCallDelegate(instance, channel: channel)
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    switch call.method {
    case "getBundledModelPath":
      result(
        FlutterError(
          code: "UNIMPLEMENTED",
          message:
            "getBundledModelPath is not implemented on iOS in this plugin build.",
          details: nil))
    default:
      result(FlutterMethodNotImplemented)
    }
  }
}

Pod::Spec.new do |s|
  s.name             = 'whispercpp_flutter'
  s.version          = '0.0.1'
  s.summary          = 'A Flutter plugin scaffold with platform channels.'
  s.description      = <<-DESC
A Flutter plugin scaffold with platform channels.
                       DESC
  s.homepage         = 'https://example.com'
  s.license          = { :file => '../LICENSE' }
  s.author           = { 'Your Name' => 'you@example.com' }
  s.source           = { :path => '.' }
  s.source_files = 'Classes/**/*'
  s.dependency 'Flutter'
  s.platform = :ios, '12.0'
  s.swift_version = '5.0'

  s.pod_target_xcconfig = {
    'DEFINES_MODULE' => 'YES',
    'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'i386'
  }
end

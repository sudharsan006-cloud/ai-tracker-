import 'package:flutter/foundation.dart';

class PlatformCapabilities {
  static bool get isWeb => kIsWeb;
  static bool get supportsLocalDb => !kIsWeb;        // used in Phase 2
  static bool get supportsSms => !kIsWeb;            // Phase 2
  static bool get supportsNativeSteps => !kIsWeb;    // Phase 4
  static bool get supportsVideoOnboarding => kIsWeb; // Part C
}

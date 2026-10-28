import 'package:shared_preferences/shared_preferences.dart';

class OnboardingFlag {
  static const _key = 'onboarding_seen_v1';

  static Future<bool> isSeen() async =>
      (await SharedPreferences.getInstance()).getBool(_key) ?? false;

  static Future<void> markSeen() async =>
      (await SharedPreferences.getInstance()).setBool(_key, true);
}

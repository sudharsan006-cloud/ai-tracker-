import 'reminder_settings.dart';

class NotificationService {
  static Future<void> initialize() async {
    // No-op on web
  }

  static Future<bool> requestPermission() async {
    // Web notifications not supported
    return false;
  }

  static Future<void> rescheduleAll(ReminderSettings settings) async {
    // No-op on web
  }

  static Future<void> scheduleWater(bool enable) async {
    // No-op on web
  }

  static Future<void> scheduleDaily({
    required int id,
    required String title,
    required String body,
    required int hour,
    required int minute,
    required bool enable,
  }) async {
    // No-op on web
  }

  static Future<void> cancelReminder(int id) async {
    // No-op on web
  }

  static Future<void> showBudgetAlert(int level) async {
    // No-op on web
  }
}

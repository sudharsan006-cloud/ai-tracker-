import 'dart:io';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:flutter_timezone/flutter_timezone.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:timezone/data/latest_all.dart' as tz;
import 'package:timezone/timezone.dart' as tz;
import 'reminder_settings.dart';

class NotificationService {
  static final FlutterLocalNotificationsPlugin _notificationsPlugin =
      FlutterLocalNotificationsPlugin();
  static bool _isInitialized = false;

  static const List<int> waterHours = [9, 11, 13, 15, 17, 19, 21];
  static const int waterBaseId = 100;
  static const int workoutId = 201;
  static const int moodId = 202;
  static const int expensesId = 203;
  static const int budgetAlertId = 301;

  static Future<void> initialize() async {
    if (_isInitialized) return;

    tz.initializeTimeZones();
    try {
      final String currentTimeZone = await FlutterTimezone.getLocalTimezone();
      tz.setLocalLocation(tz.getLocation(currentTimeZone));
    } catch (_) {
      // Fallback if local timezone lookup fails
      tz.setLocalLocation(tz.getLocation('Asia/Kolkata'));
    }

    const AndroidInitializationSettings androidSettings =
        AndroidInitializationSettings('@mipmap/ic_launcher');

    const InitializationSettings settings = InitializationSettings(
      android: androidSettings,
    );

    await _notificationsPlugin.initialize(settings);
    _isInitialized = true;
  }

  static Future<bool> requestPermission() async {
    if (!Platform.isAndroid) return false;

    // Use permission_handler for notification permission
    final status = await Permission.notification.status;
    if (status.isGranted) return true;

    final result = await Permission.notification.request();
    return result.isGranted;
  }

  static Future<void> rescheduleAll(ReminderSettings settings) async {
    await initialize();

    // Water
    if (settings.waterEnabled) {
      await scheduleWater(true);
    } else {
      await scheduleWater(false);
    }

    // Workout
    await scheduleDaily(
      id: workoutId,
      title: 'Workout',
      body: "Ready for today's workout?",
      hour: settings.workoutTime.hour,
      minute: settings.workoutTime.minute,
      enable: settings.workoutEnabled,
    );

    // Mood
    await scheduleDaily(
      id: moodId,
      title: 'Mood',
      body: 'How was your day? Log your mood.',
      hour: settings.moodTime.hour,
      minute: settings.moodTime.minute,
      enable: settings.moodEnabled,
    );

    // Expenses
    await scheduleDaily(
      id: expensesId,
      title: 'Expenses',
      body: "Check today's spending in the app.",
      hour: settings.expensesTime.hour,
      minute: settings.expensesTime.minute,
      enable: settings.expensesEnabled,
    );
  }

  static Future<void> scheduleWater(bool enable) async {
    await initialize();
    for (int i = 0; i < waterHours.length; i++) {
      final id = waterBaseId + i + 1;
      await _notificationsPlugin.cancel(id);
      if (enable) {
        final hour = waterHours[i];
        final scheduledTime = _nextInstanceOfTime(hour, 0);

        await _notificationsPlugin.zonedSchedule(
          id,
          'Water',
          'Time for a glass of water.',
          scheduledTime,
          const NotificationDetails(
            android: AndroidNotificationDetails(
              'reminders_channel',
              'Daily Reminders',
              channelDescription: 'Scheduled reminders for habits and wellness',
              importance: Importance.defaultImportance,
              priority: Priority.defaultPriority,
            ),
          ),
          androidScheduleMode: AndroidScheduleMode.inexactAllowWhileIdle,
          uiLocalNotificationDateInterpretation:
              UILocalNotificationDateInterpretation.absoluteTime,
          matchDateTimeComponents: DateTimeComponents.time,
        );
      }
    }
  }

  static Future<void> scheduleDaily({
    required int id,
    required String title,
    required String body,
    required int hour,
    required int minute,
    required bool enable,
  }) async {
    await initialize();
    await _notificationsPlugin.cancel(id);

    if (enable) {
      final scheduledTime = _nextInstanceOfTime(hour, minute);

      await _notificationsPlugin.zonedSchedule(
        id,
        title,
        body,
        scheduledTime,
        const NotificationDetails(
          android: AndroidNotificationDetails(
            'reminders_channel',
            'Daily Reminders',
            channelDescription: 'Scheduled reminders for habits and wellness',
            importance: Importance.defaultImportance,
            priority: Priority.defaultPriority,
          ),
        ),
        androidScheduleMode: AndroidScheduleMode.inexactAllowWhileIdle,
        uiLocalNotificationDateInterpretation:
            UILocalNotificationDateInterpretation.absoluteTime,
        matchDateTimeComponents: DateTimeComponents.time,
      );
    }
  }

  static Future<void> cancelReminder(int id) async {
    await initialize();
    await _notificationsPlugin.cancel(id);
  }

  static Future<void> showBudgetAlert(int level) async {
    await initialize();
    String message;
    if (level >= 100) {
      message = 'Budget reached: one of your budgets is fully used.';
    } else {
      message = 'Budget alert: one of your budgets is at 80%.';
    }

    await _notificationsPlugin.show(
      budgetAlertId,
      'Budget Alert',
      message,
      const NotificationDetails(
        android: AndroidNotificationDetails(
          'budget_alerts_channel',
          'Budget Alerts',
          channelDescription: 'Notifications when budgets reach threshold limits',
          importance: Importance.high,
          priority: Priority.high,
        ),
      ),
    );
  }

  static tz.TZDateTime _nextInstanceOfTime(int hour, int minute) {
    final tz.TZDateTime now = tz.TZDateTime.now(tz.local);
    tz.TZDateTime scheduledDate = tz.TZDateTime(
      tz.local,
      now.year,
      now.month,
      now.day,
      hour,
      minute,
    );
    if (scheduledDate.isBefore(now)) {
      scheduledDate = scheduledDate.add(const Duration(days: 1));
    }
    return scheduledDate;
  }
}

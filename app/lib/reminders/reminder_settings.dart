import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

class ReminderSettings {
  bool waterEnabled;
  bool workoutEnabled;
  TimeOfDay workoutTime;
  bool moodEnabled;
  TimeOfDay moodTime;
  bool expensesEnabled;
  TimeOfDay expensesTime;
  bool budgetAlertsEnabled;

  ReminderSettings({
    this.waterEnabled = false,
    this.workoutEnabled = false,
    this.workoutTime = const TimeOfDay(hour: 18, minute: 0),
    this.moodEnabled = false,
    this.moodTime = const TimeOfDay(hour: 21, minute: 0),
    this.expensesEnabled = false,
    this.expensesTime = const TimeOfDay(hour: 20, minute: 0),
    this.budgetAlertsEnabled = true,
  });

  static const String keyWaterEnabled = 'reminder_water_enabled';
  static const String keyWorkoutEnabled = 'reminder_workout_enabled';
  static const String keyWorkoutHour = 'reminder_workout_hour';
  static const String keyWorkoutMinute = 'reminder_workout_minute';
  static const String keyMoodEnabled = 'reminder_mood_enabled';
  static const String keyMoodHour = 'reminder_mood_hour';
  static const String keyMoodMinute = 'reminder_mood_minute';
  static const String keyExpensesEnabled = 'reminder_expenses_enabled';
  static const String keyExpensesHour = 'reminder_expenses_hour';
  static const String keyExpensesMinute = 'reminder_expenses_minute';
  static const String keyBudgetAlertsEnabled = 'reminder_budget_alerts_enabled';

  static Future<ReminderSettings> load() async {
    final prefs = await SharedPreferences.getInstance();
    return ReminderSettings(
      waterEnabled: prefs.getBool(keyWaterEnabled) ?? false,
      workoutEnabled: prefs.getBool(keyWorkoutEnabled) ?? false,
      workoutTime: TimeOfDay(
        hour: prefs.getInt(keyWorkoutHour) ?? 18,
        minute: prefs.getInt(keyWorkoutMinute) ?? 0,
      ),
      moodEnabled: prefs.getBool(keyMoodEnabled) ?? false,
      moodTime: TimeOfDay(
        hour: prefs.getInt(keyMoodHour) ?? 21,
        minute: prefs.getInt(keyMoodMinute) ?? 0,
      ),
      expensesEnabled: prefs.getBool(keyExpensesEnabled) ?? false,
      expensesTime: TimeOfDay(
        hour: prefs.getInt(keyExpensesHour) ?? 20,
        minute: prefs.getInt(keyExpensesMinute) ?? 0,
      ),
      budgetAlertsEnabled: prefs.getBool(keyBudgetAlertsEnabled) ?? true,
    );
  }

  Future<void> save() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(keyWaterEnabled, waterEnabled);
    await prefs.setBool(keyWorkoutEnabled, workoutEnabled);
    await prefs.setInt(keyWorkoutHour, workoutTime.hour);
    await prefs.setInt(keyWorkoutMinute, workoutTime.minute);
    await prefs.setBool(keyMoodEnabled, moodEnabled);
    await prefs.setInt(keyMoodHour, moodTime.hour);
    await prefs.setInt(keyMoodMinute, moodTime.minute);
    await prefs.setBool(keyExpensesEnabled, expensesEnabled);
    await prefs.setInt(keyExpensesHour, expensesTime.hour);
    await prefs.setInt(keyExpensesMinute, expensesTime.minute);
    await prefs.setBool(keyBudgetAlertsEnabled, budgetAlertsEnabled);
  }
}

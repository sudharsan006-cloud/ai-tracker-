import 'package:shared_preferences/shared_preferences.dart';
import '../expenses/expense_repository.dart';
import '../reminders/notification_service.dart';
import '../reminders/reminder_settings.dart';

class BudgetAlertService {
  static Future<void> checkBudgetsAndNotify(ExpenseRepository repository) async {
    final settings = await ReminderSettings.load();
    if (!settings.budgetAlertsEnabled) return;

    try {
      final budgets = await repository.getBudgets();
      final now = DateTime.now();
      final yyyyMM = '${now.year}${now.month.toString().padLeft(2, '0')}';
      final prefs = await SharedPreferences.getInstance();

      bool hasTriggered100 = false;
      bool hasTriggered80 = false;

      for (final budget in budgets) {
        final key = 'budget_alert_${budget.id}_$yyyyMM';
        final lastLevel = prefs.getInt(key) ?? 0;

        if (budget.percentUsed >= 100 && lastLevel < 100) {
          await prefs.setInt(key, 100);
          if (!hasTriggered100) {
            await NotificationService.showBudgetAlert(100);
            hasTriggered100 = true;
          }
        } else if (budget.percentUsed >= 80 && lastLevel < 80) {
          await prefs.setInt(key, 80);
          if (!hasTriggered80 && !hasTriggered100) {
            await NotificationService.showBudgetAlert(80);
            hasTriggered80 = true;
          }
        }
      }
    } catch (_) {
      // Non-fatal if offline or backend unavailable
    }
  }

  // Pure logic helper for testing
  static int evaluateAlertLevel({
    required num percentUsed,
    required int lastLevel,
  }) {
    if (percentUsed >= 100 && lastLevel < 100) {
      return 100;
    } else if (percentUsed >= 80 && lastLevel < 80) {
      return 80;
    }
    return lastLevel;
  }
}

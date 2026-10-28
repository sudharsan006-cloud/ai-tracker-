import 'package:ai_life_tracker/budgets/budget_alert_service.dart';
import 'package:ai_life_tracker/core/widgets/empty_state_widget.dart';
import 'package:ai_life_tracker/reminders/reminder_settings.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('Phase 6 - ReminderSettings', () {
    test('Saves and loads reminder preferences accurately', () async {
      SharedPreferences.setMockInitialValues({});

      final settings = ReminderSettings(
        waterEnabled: true,
        workoutEnabled: true,
        workoutTime: const TimeOfDay(hour: 7, minute: 30),
        moodEnabled: true,
        moodTime: const TimeOfDay(hour: 22, minute: 15),
        expensesEnabled: true,
        expensesTime: const TimeOfDay(hour: 20, minute: 45),
        budgetAlertsEnabled: true,
      );

      await settings.save();

      final loaded = await ReminderSettings.load();
      expect(loaded.waterEnabled, isTrue);
      expect(loaded.workoutEnabled, isTrue);
      expect(loaded.workoutTime.hour, 7);
      expect(loaded.workoutTime.minute, 30);
      expect(loaded.moodEnabled, isTrue);
      expect(loaded.moodTime.hour, 22);
      expect(loaded.moodTime.minute, 15);
      expect(loaded.expensesEnabled, isTrue);
      expect(loaded.expensesTime.hour, 20);
      expect(loaded.expensesTime.minute, 45);
      expect(loaded.budgetAlertsEnabled, isTrue);
    });
  });

  group('Phase 6 - Budget Alert Logic', () {
    test('Calculates threshold levels without repeat alerts', () {
      // 1. Initial under 80%
      int level = BudgetAlertService.evaluateAlertLevel(
        percentUsed: 65,
        lastLevel: 0,
      );
      expect(level, 0);

      // 2. Reaches 80% -> triggers level 80
      level = BudgetAlertService.evaluateAlertLevel(
        percentUsed: 82,
        lastLevel: 0,
      );
      expect(level, 80);

      // 3. Stays in 80% zone (e.g. 95%) -> no repeat
      int repeatLevel = BudgetAlertService.evaluateAlertLevel(
        percentUsed: 95,
        lastLevel: 80,
      );
      expect(repeatLevel, 80);

      // 4. Reaches 100% -> triggers level 100
      level = BudgetAlertService.evaluateAlertLevel(
        percentUsed: 105,
        lastLevel: 80,
      );
      expect(level, 100);

      // 5. Stays over 100% -> no repeat
      repeatLevel = BudgetAlertService.evaluateAlertLevel(
        percentUsed: 120,
        lastLevel: 100,
      );
      expect(repeatLevel, 100);
    });
  });

  group('Phase 6 - EmptyStateWidget', () {
    testWidgets('Renders icon, message, and responds to action button tap', (tester) async {
      bool actionTapped = false;

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: EmptyStateWidget(
              icon: Icons.savings_outlined,
              message: 'No budgets configured for this month.',
              actionLabel: 'Add Budget',
              onAction: () {
                actionTapped = true;
              },
            ),
          ),
        ),
      );

      expect(find.byIcon(Icons.savings_outlined), findsOneWidget);
      expect(find.text('No budgets configured for this month.'), findsOneWidget);
      expect(find.text('Add Budget'), findsOneWidget);

      await tester.tap(find.text('Add Budget'));
      await tester.pump();

      expect(actionTapped, isTrue);
    });
  });
}

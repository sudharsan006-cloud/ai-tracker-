import 'package:flutter/material.dart';
import '../core/platform_capabilities.dart';
import 'notification_service.dart';
import 'reminder_settings.dart';

class RemindersScreen extends StatefulWidget {
  const RemindersScreen({super.key});

  @override
  State<RemindersScreen> createState() => _RemindersScreenState();
}

class _RemindersScreenState extends State<RemindersScreen> {
  ReminderSettings? _settings;
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _loadSettings();
  }

  Future<void> _loadSettings() async {
    final s = await ReminderSettings.load();
    if (mounted) {
      setState(() {
        _settings = s;
        _isLoading = false;
      });
    }
  }

  Future<bool> _ensurePermission() async {
    final granted = await NotificationService.requestPermission();
    if (!granted && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Notifications are blocked. Allow them in phone settings.'),
        ),
      );
    }
    return granted;
  }

  Future<void> _toggleWater(bool val) async {
    if (_settings == null) return;
    if (val) {
      final granted = await _ensurePermission();
      if (!granted) return;
    }
    setState(() => _settings!.waterEnabled = val);
    await _settings!.save();
    await NotificationService.scheduleWater(val);
  }

  Future<void> _toggleWorkout(bool val) async {
    if (_settings == null) return;
    if (val) {
      final granted = await _ensurePermission();
      if (!granted) return;
    }
    setState(() => _settings!.workoutEnabled = val);
    await _settings!.save();
    await NotificationService.scheduleDaily(
      id: 201,
      title: 'Workout',
      body: "Ready for today's workout?",
      hour: _settings!.workoutTime.hour,
      minute: _settings!.workoutTime.minute,
      enable: val,
    );
  }

  Future<void> _pickWorkoutTime() async {
    if (_settings == null) return;
    final picked = await showTimePicker(
      context: context,
      initialTime: _settings!.workoutTime,
    );
    if (picked != null) {
      setState(() => _settings!.workoutTime = picked);
      await _settings!.save();
      if (_settings!.workoutEnabled) {
        await NotificationService.scheduleDaily(
          id: 201,
          title: 'Workout',
          body: "Ready for today's workout?",
          hour: picked.hour,
          minute: picked.minute,
          enable: true,
        );
      }
    }
  }

  Future<void> _toggleMood(bool val) async {
    if (_settings == null) return;
    if (val) {
      final granted = await _ensurePermission();
      if (!granted) return;
    }
    setState(() => _settings!.moodEnabled = val);
    await _settings!.save();
    await NotificationService.scheduleDaily(
      id: 202,
      title: 'Mood',
      body: 'How was your day? Log your mood.',
      hour: _settings!.moodTime.hour,
      minute: _settings!.moodTime.minute,
      enable: val,
    );
  }

  Future<void> _pickMoodTime() async {
    if (_settings == null) return;
    final picked = await showTimePicker(
      context: context,
      initialTime: _settings!.moodTime,
    );
    if (picked != null) {
      setState(() => _settings!.moodTime = picked);
      await _settings!.save();
      if (_settings!.moodEnabled) {
        await NotificationService.scheduleDaily(
          id: 202,
          title: 'Mood',
          body: 'How was your day? Log your mood.',
          hour: picked.hour,
          minute: picked.minute,
          enable: true,
        );
      }
    }
  }

  Future<void> _toggleExpenses(bool val) async {
    if (_settings == null) return;
    if (val) {
      final granted = await _ensurePermission();
      if (!granted) return;
    }
    setState(() => _settings!.expensesEnabled = val);
    await _settings!.save();
    await NotificationService.scheduleDaily(
      id: 203,
      title: 'Expenses',
      body: "Check today's spending in the app.",
      hour: _settings!.expensesTime.hour,
      minute: _settings!.expensesTime.minute,
      enable: val,
    );
  }

  Future<void> _pickExpensesTime() async {
    if (_settings == null) return;
    final picked = await showTimePicker(
      context: context,
      initialTime: _settings!.expensesTime,
    );
    if (picked != null) {
      setState(() => _settings!.expensesTime = picked);
      await _settings!.save();
      if (_settings!.expensesEnabled) {
        await NotificationService.scheduleDaily(
          id: 203,
          title: 'Expenses',
          body: "Check today's spending in the app.",
          hour: picked.hour,
          minute: picked.minute,
          enable: true,
        );
      }
    }
  }

  Future<void> _toggleBudgetAlerts(bool val) async {
    if (_settings == null) return;
    setState(() => _settings!.budgetAlertsEnabled = val);
    await _settings!.save();
  }

  @override
  Widget build(BuildContext context) {
    final isWeb = PlatformCapabilities.isWeb;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Reminders'),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              children: [
                if (isWeb)
                  Container(
                    padding: const EdgeInsets.all(12),
                    margin: const EdgeInsets.only(bottom: 16),
                    decoration: BoxDecoration(
                      color: Colors.amber.withOpacity(0.15),
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(color: Colors.amber.shade700),
                    ),
                    child: const Row(
                      children: [
                        Icon(Icons.info_outline, color: Colors.amber),
                        SizedBox(width: 12),
                        Expanded(
                          child: Text(
                            'Reminders are available on the Android app.',
                            style: TextStyle(fontWeight: FontWeight.w500),
                          ),
                        ),
                      ],
                    ),
                  ),

                // Water
                SwitchListTile(
                  title: const Text('Water'),
                  subtitle: const Text('Every 2 hours, 09:00 to 21:00'),
                  value: _settings!.waterEnabled,
                  onChanged: isWeb ? null : _toggleWater,
                  secondary: const Icon(Icons.water_drop_outlined),
                ),
                const Divider(),

                // Workout
                SwitchListTile(
                  title: const Text('Workout'),
                  subtitle: Text(
                    'Daily at ${_settings!.workoutTime.format(context)}',
                  ),
                  value: _settings!.workoutEnabled,
                  onChanged: isWeb ? null : _toggleWorkout,
                  secondary: const Icon(Icons.fitness_center),
                ),
                if (!isWeb && _settings!.workoutEnabled)
                  ListTile(
                    title: const Text('Change workout reminder time'),
                    trailing: Text(
                      _settings!.workoutTime.format(context),
                      style: TextStyle(
                        color: Theme.of(context).colorScheme.primary,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    onTap: _pickWorkoutTime,
                  ),
                const Divider(),

                // Mood
                SwitchListTile(
                  title: const Text('Mood'),
                  subtitle: Text(
                    'Daily at ${_settings!.moodTime.format(context)}',
                  ),
                  value: _settings!.moodEnabled,
                  onChanged: isWeb ? null : _toggleMood,
                  secondary: const Icon(Icons.sentiment_satisfied_alt),
                ),
                if (!isWeb && _settings!.moodEnabled)
                  ListTile(
                    title: const Text('Change mood reminder time'),
                    trailing: Text(
                      _settings!.moodTime.format(context),
                      style: TextStyle(
                        color: Theme.of(context).colorScheme.primary,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    onTap: _pickMoodTime,
                  ),
                const Divider(),

                // Expenses
                SwitchListTile(
                  title: const Text('Expenses'),
                  subtitle: Text(
                    'Daily at ${_settings!.expensesTime.format(context)}',
                  ),
                  value: _settings!.expensesEnabled,
                  onChanged: isWeb ? null : _toggleExpenses,
                  secondary: const Icon(Icons.receipt_long),
                ),
                if (!isWeb && _settings!.expensesEnabled)
                  ListTile(
                    title: const Text('Change expenses reminder time'),
                    trailing: Text(
                      _settings!.expensesTime.format(context),
                      style: TextStyle(
                        color: Theme.of(context).colorScheme.primary,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    onTap: _pickExpensesTime,
                  ),
                const Divider(),

                // Budget Alerts
                SwitchListTile(
                  title: const Text('Budget alerts'),
                  subtitle: const Text('Notify when budgets reach 80% or 100%'),
                  value: _settings!.budgetAlertsEnabled,
                  onChanged: isWeb ? null : _toggleBudgetAlerts,
                  secondary: const Icon(Icons.notifications_active_outlined),
                ),
              ],
            ),
    );
  }
}

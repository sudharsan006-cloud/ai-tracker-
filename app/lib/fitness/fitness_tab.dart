import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import '../core/api_client.dart';
import '../core/platform_capabilities.dart';
import '../exercises/exercise_library_screen.dart';
import '../steps/steps_sync_service.dart';
import 'charts_screen.dart';
import 'fitness_repository.dart';
import 'goals_screen.dart';
import 'models.dart';
import 'workout_form_screen.dart';
import 'workouts_screen.dart';

class FitnessTab extends StatefulWidget {
  const FitnessTab({super.key});

  @override
  State<FitnessTab> createState() => _FitnessTabState();
}

class _FitnessTabState extends State<FitnessTab> {
  DateTime _selectedDate = DateTime.now();
  bool _isLoading = true;
  String? _errorMessage;

  DailySummary? _summary;
  List<WaterEntry> _waterEntries = [];
  List<CalorieEntry> _calorieEntries = [];
  List<Workout> _dayWorkouts = [];

  bool _isSyncingSteps = false;

  @override
  void initState() {
    super.initState();
    _loadAllData();
    _triggerInitialStepSync();
  }

  void _triggerInitialStepSync() {
    if (PlatformCapabilities.supportsNativeSteps && _isToday(_selectedDate)) {
      _syncSteps();
    }
  }

  bool _isToday(DateTime date) {
    final now = DateTime.now();
    return date.year == now.year && date.month == now.month && date.day == now.day;
  }

  String get _dateStr => DateFormat('yyyy-MM-dd').format(_selectedDate);

  Future<void> _loadAllData() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    final repo = FitnessRepository(context.read<ApiClient>());
    try {
      final summaryFuture = repo.getDailySummary(date: _dateStr);
      final waterFuture = repo.getWaterEntries(date: _dateStr);
      final calorieFuture = repo.getCalorieEntries(date: _dateStr);
      final workoutsFuture = repo.getWorkouts(from: _dateStr, to: _dateStr);

      final results = await Future.wait([
        summaryFuture,
        waterFuture,
        calorieFuture,
        workoutsFuture,
      ]);

      if (mounted) {
        setState(() {
          _summary = results[0] as DailySummary;
          _waterEntries = results[1] as List<WaterEntry>;
          _calorieEntries = results[2] as List<CalorieEntry>;
          final workoutPage = results[3] as Map<String, dynamic>;
          _dayWorkouts = workoutPage['items'] as List<Workout>;
          _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _errorMessage = e.toString().replaceFirst('Exception: ', '');
          _isLoading = false;
        });
      }
    }
  }

  Future<void> _syncSteps() async {
    if (!PlatformCapabilities.supportsNativeSteps) return;
    setState(() => _isSyncingSteps = true);

    try {
      final syncService = StepsSyncService(context.read<ApiClient>());
      final result = await syncService.sync();
      if (mounted) {
        if (result == null) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Activity permission is needed to count steps')),
          );
        }
        await _loadAllData();
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Step sync failed: $e')),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _isSyncingSteps = false);
      }
    }
  }

  void _changeDate(int offsetDays) {
    final newDate = _selectedDate.add(Duration(days: offsetDays));
    final now = DateTime.now();
    if (newDate.isAfter(DateTime(now.year, now.month, now.day))) {
      return;
    }
    setState(() {
      _selectedDate = newDate;
    });
    _loadAllData();
  }

  void _editStepsDialog() async {
    final controller = TextEditingController(text: _summary?.steps.toString() ?? '0');
    final steps = await showDialog<int>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Edit Steps'),
        content: TextField(
          controller: controller,
          keyboardType: TextInputType.number,
          autofocus: true,
          decoration: const InputDecoration(
            labelText: 'Steps',
            helperText: 'Enter steps count (0 - 200,000)',
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancel')),
          ElevatedButton(
            onPressed: () {
              final val = int.tryParse(controller.text.trim());
              if (val != null && val >= 0 && val <= 200000) {
                Navigator.pop(ctx, val);
              }
            },
            child: const Text('Save'),
          ),
        ],
      ),
    );

    if (steps != null && mounted) {
      final repo = FitnessRepository(context.read<ApiClient>());
      try {
        await repo.updateSteps(
          date: _dateStr,
          steps: steps,
          source: 'manual',
        );
        _loadAllData();
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Error: $e')));
        }
      }
    }
  }

  Future<void> _addWater(int amountMl) async {
    final repo = FitnessRepository(context.read<ApiClient>());
    try {
      await repo.addWater(amountMl: amountMl, date: _dateStr);
      _loadAllData();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Error: $e')));
      }
    }
  }

  void _customWaterDialog() async {
    final controller = TextEditingController();
    final amount = await showDialog<int>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Add Water'),
        content: TextField(
          controller: controller,
          keyboardType: TextInputType.number,
          autofocus: true,
          decoration: const InputDecoration(
            labelText: 'Amount (ml)',
            hintText: 'e.g. 300',
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancel')),
          ElevatedButton(
            onPressed: () {
              final val = int.tryParse(controller.text.trim());
              if (val != null && val >= 1 && val <= 5000) {
                Navigator.pop(ctx, val);
              }
            },
            child: const Text('Add'),
          ),
        ],
      ),
    );

    if (amount != null) {
      _addWater(amount);
    }
  }

  Future<void> _deleteWater(String id) async {
    final repo = FitnessRepository(context.read<ApiClient>());
    try {
      await repo.deleteWaterEntry(id);
      _loadAllData();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Error: $e')));
      }
    }
  }

  void _addFoodDialog() async {
    final kcalController = TextEditingController();
    final labelController = TextEditingController();

    final result = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Add Food / Calories'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: kcalController,
              keyboardType: TextInputType.number,
              autofocus: true,
              decoration: const InputDecoration(
                labelText: 'Calories (kcal)*',
                hintText: 'e.g. 350',
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: labelController,
              decoration: const InputDecoration(
                labelText: 'Label (optional)',
                hintText: 'e.g. Chicken Rice',
              ),
            ),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          ElevatedButton(
            onPressed: () {
              final kcal = int.tryParse(kcalController.text.trim());
              if (kcal != null && kcal >= 1 && kcal <= 10000) {
                Navigator.pop(ctx, true);
              }
            },
            child: const Text('Add'),
          ),
        ],
      ),
    );

    if (result == true && mounted) {
      final kcal = int.tryParse(kcalController.text.trim())!;
      final label = labelController.text.trim().isEmpty ? null : labelController.text.trim();
      final repo = FitnessRepository(context.read<ApiClient>());
      try {
        await repo.addCalorie(kcal: kcal, label: label, date: _dateStr);
        _loadAllData();
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Error: $e')));
        }
      }
    }
  }

  Future<void> _deleteCalorie(String id) async {
    final repo = FitnessRepository(context.read<ApiClient>());
    try {
      await repo.deleteCalorieEntry(id);
      _loadAllData();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Error: $e')));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final isTodayDate = _isToday(_selectedDate);

    return Scaffold(
      body: RefreshIndicator(
        onRefresh: () async {
          if (PlatformCapabilities.supportsNativeSteps && isTodayDate) {
            await _syncSteps();
          } else {
            await _loadAllData();
          }
        },
        child: SingleChildScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.all(16.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // Date bar
              _buildDateBar(isTodayDate),
              const SizedBox(height: 16),

              // Navigation buttons row
              _buildButtonsRow(),
              const SizedBox(height: 16),

              if (_isLoading)
                const Padding(
                  padding: EdgeInsets.symmetric(vertical: 48.0),
                  child: Center(child: CircularProgressIndicator()),
                )
              else if (_errorMessage != null)
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: Colors.red.shade900.withOpacity(0.3),
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(color: Colors.red.shade700),
                  ),
                  child: Text(_errorMessage!, style: const TextStyle(color: Colors.redAccent)),
                )
              else ...[
                // Steps card
                _buildStepsCard(),
                const SizedBox(height: 16),

                // Water card
                _buildWaterCard(),
                const SizedBox(height: 16),

                // Calories card
                _buildCaloriesCard(),
                const SizedBox(height: 16),

                // Workouts card
                _buildWorkoutsCard(),
                const SizedBox(height: 32),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildDateBar(bool isTodayDate) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8.0, vertical: 4.0),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            IconButton(
              icon: const Icon(Icons.chevron_left),
              tooltip: 'Previous day',
              onPressed: () => _changeDate(-1),
            ),
            Row(
              children: [
                const Icon(Icons.calendar_today, size: 16, color: Colors.white70),
                const SizedBox(width: 8),
                Text(
                  isTodayDate ? 'Today (${_dateStr})' : _dateStr,
                  style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                ),
              ],
            ),
            IconButton(
              icon: const Icon(Icons.chevron_right),
              tooltip: 'Next day',
              onPressed: isTodayDate ? null : () => _changeDate(1),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildButtonsRow() {
    return Row(
      children: [
        Expanded(
          child: OutlinedButton(
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const ExerciseLibraryScreen()),
            ),
            style: OutlinedButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 12)),
            child: const Column(
              children: [
                Icon(Icons.library_books, size: 20),
                SizedBox(height: 4),
                Text('Exercises', style: TextStyle(fontSize: 12)),
              ],
            ),
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: OutlinedButton(
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const WorkoutsScreen()),
            ),
            style: OutlinedButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 12)),
            child: const Column(
              children: [
                Icon(Icons.fitness_center, size: 20),
                SizedBox(height: 4),
                Text('Workouts', style: TextStyle(fontSize: 12)),
              ],
            ),
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: OutlinedButton(
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const ChartsScreen()),
            ),
            style: OutlinedButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 12)),
            child: const Column(
              children: [
                Icon(Icons.bar_chart, size: 20),
                SizedBox(height: 4),
                Text('Charts', style: TextStyle(fontSize: 12)),
              ],
            ),
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: OutlinedButton(
            onPressed: () async {
              final updated = await Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const GoalsScreen()),
              );
              if (updated == true) _loadAllData();
            },
            style: OutlinedButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 12)),
            child: const Column(
              children: [
                Icon(Icons.flag, size: 20),
                SizedBox(height: 4),
                Text('Goals', style: TextStyle(fontSize: 12)),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildStepsCard() {
    final s = _summary!;
    final sourceText = s.stepsSource == 'sensor'
        ? 'from phone sensor'
        : s.stepsSource == 'manual'
            ? 'entered manually'
            : 'no data yet';

    final ringValue = (s.stepsPercent / 100.0).clamp(0.0, 1.0);

    return Card(
      elevation: 2,
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          children: [
            Row(
              children: [
                Stack(
                  alignment: Alignment.center,
                  children: [
                    SizedBox(
                      width: 64,
                      height: 64,
                      child: CircularProgressIndicator(
                        value: ringValue,
                        strokeWidth: 6,
                        backgroundColor: Colors.white10,
                        color: Colors.blueAccent,
                      ),
                    ),
                    const Icon(Icons.directions_walk, size: 28, color: Colors.blueAccent),
                  ],
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        '${s.steps}',
                        style: const TextStyle(fontSize: 26, fontWeight: FontWeight.bold),
                      ),
                      Text(
                        'of ${s.stepsGoal} steps (${s.stepsPercent}%)',
                        style: const TextStyle(color: Colors.white70, fontSize: 14),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        sourceText,
                        style: const TextStyle(fontSize: 12, color: Colors.white38),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                TextButton.icon(
                  onPressed: _editStepsDialog,
                  icon: const Icon(Icons.edit, size: 16),
                  label: const Text('Edit'),
                ),
                if (PlatformCapabilities.supportsNativeSteps) ...[
                  const SizedBox(width: 8),
                  ElevatedButton.icon(
                    onPressed: _isSyncingSteps ? null : _syncSteps,
                    icon: _isSyncingSteps
                        ? const SizedBox(
                            width: 16,
                            height: 16,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.sync, size: 16),
                    label: const Text('Sync steps'),
                  ),
                ],
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildWaterCard() {
    final s = _summary!;
    final ringValue = (s.waterPercent / 100.0).clamp(0.0, 1.0);

    return Card(
      elevation: 2,
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Stack(
                  alignment: Alignment.center,
                  children: [
                    SizedBox(
                      width: 64,
                      height: 64,
                      child: CircularProgressIndicator(
                        value: ringValue,
                        strokeWidth: 6,
                        backgroundColor: Colors.white10,
                        color: Colors.cyanAccent,
                      ),
                    ),
                    const Icon(Icons.local_drink, size: 28, color: Colors.cyanAccent),
                  ],
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        '${s.waterMl} ml',
                        style: const TextStyle(fontSize: 26, fontWeight: FontWeight.bold),
                      ),
                      Text(
                        'of ${s.waterGoalMl} ml (${s.waterPercent}%)',
                        style: const TextStyle(color: Colors.white70, fontSize: 14),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),

            // Quick add buttons
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                ActionChip(
                  label: const Text('+150 ml'),
                  avatar: const Icon(Icons.add, size: 16),
                  onPressed: () => _addWater(150),
                ),
                ActionChip(
                  label: const Text('+250 ml'),
                  avatar: const Icon(Icons.add, size: 16),
                  onPressed: () => _addWater(250),
                ),
                ActionChip(
                  label: const Text('+500 ml'),
                  avatar: const Icon(Icons.add, size: 16),
                  onPressed: () => _addWater(500),
                ),
                ActionChip(
                  label: const Text('Custom'),
                  avatar: const Icon(Icons.edit, size: 16),
                  onPressed: _customWaterDialog,
                ),
              ],
            ),

            if (_waterEntries.isNotEmpty) ...[
              const Divider(height: 24),
              const Text(
                "Today's Drinks",
                style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: Colors.white70),
              ),
              const SizedBox(height: 8),
              ..._waterEntries.map((entry) {
                final timeStr = DateFormat('HH:mm').format(entry.loggedAt.toLocal());
                return Padding(
                  padding: const EdgeInsets.symmetric(vertical: 4.0),
                  child: Row(
                    children: [
                      const Icon(Icons.water_drop, size: 16, color: Colors.cyanAccent),
                      const SizedBox(width: 8),
                      Text(timeStr, style: const TextStyle(color: Colors.white60, fontSize: 13)),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Text(
                          '${entry.amountMl} ml',
                          style: const TextStyle(fontWeight: FontWeight.w600),
                        ),
                      ),
                      IconButton(
                        icon: const Icon(Icons.close, size: 18, color: Colors.white38),
                        onPressed: () => _deleteWater(entry.id),
                      ),
                    ],
                  ),
                );
              }),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildCaloriesCard() {
    final s = _summary!;
    final hasGoal = s.calorieGoal != null;
    final ringValue = hasGoal && s.caloriesPercent != null
        ? (s.caloriesPercent! / 100.0).clamp(0.0, 1.0)
        : 0.0;

    return Card(
      elevation: 2,
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Stack(
                  alignment: Alignment.center,
                  children: [
                    SizedBox(
                      width: 64,
                      height: 64,
                      child: CircularProgressIndicator(
                        value: ringValue,
                        strokeWidth: 6,
                        backgroundColor: Colors.white10,
                        color: Colors.orangeAccent,
                      ),
                    ),
                    const Icon(Icons.restaurant, size: 28, color: Colors.orangeAccent),
                  ],
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        '${s.caloriesIn} kcal',
                        style: const TextStyle(fontSize: 26, fontWeight: FontWeight.bold),
                      ),
                      if (hasGoal)
                        Text(
                          'of ${s.calorieGoal} kcal (${s.caloriesPercent}%)',
                          style: const TextStyle(color: Colors.white70, fontSize: 14),
                        )
                      else
                        const Text('No daily calorie goal set', style: TextStyle(color: Colors.white38, fontSize: 12)),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),

            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  'Burned in workouts (estimate): ${s.caloriesBurnedEst} kcal',
                  style: const TextStyle(fontSize: 13, color: Colors.white70),
                ),
                ElevatedButton.icon(
                  onPressed: _addFoodDialog,
                  icon: const Icon(Icons.add, size: 16),
                  label: const Text('Add food'),
                  style: ElevatedButton.styleFrom(visualDensity: VisualDensity.compact),
                ),
              ],
            ),

            const SizedBox(height: 10),
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: Colors.white.withOpacity(0.05),
                borderRadius: BorderRadius.circular(6),
              ),
              child: s.bmrEstimate != null
                  ? Text(
                      'Resting energy estimate (BMR): ${s.bmrEstimate} kcal. A calculation from your profile.',
                      style: const TextStyle(fontSize: 12, color: Colors.white70),
                    )
                  : const Text(
                      'Add sex, birth date, height and weight in your profile to see a resting energy estimate.',
                      style: TextStyle(fontSize: 12, color: Colors.white60),
                    ),
            ),

            if (_calorieEntries.isNotEmpty) ...[
              const Divider(height: 24),
              const Text(
                "Today's Food Entries",
                style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: Colors.white70),
              ),
              const SizedBox(height: 8),
              ..._calorieEntries.map((entry) {
                final timeStr = DateFormat('HH:mm').format(entry.loggedAt.toLocal());
                return Padding(
                  padding: const EdgeInsets.symmetric(vertical: 4.0),
                  child: Row(
                    children: [
                      const Icon(Icons.fastfood, size: 16, color: Colors.orangeAccent),
                      const SizedBox(width: 8),
                      Text(timeStr, style: const TextStyle(color: Colors.white60, fontSize: 13)),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Text(
                          entry.label != null && entry.label!.isNotEmpty
                              ? '${entry.label} (${entry.kcal} kcal)'
                              : '${entry.kcal} kcal',
                          style: const TextStyle(fontWeight: FontWeight.w600),
                        ),
                      ),
                      IconButton(
                        icon: const Icon(Icons.close, size: 18, color: Colors.white38),
                        onPressed: () => _deleteCalorie(entry.id),
                      ),
                    ],
                  ),
                );
              }),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildWorkoutsCard() {
    return Card(
      elevation: 2,
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Row(
                  children: [
                    Icon(Icons.fitness_center, size: 20, color: Colors.greenAccent),
                    SizedBox(width: 8),
                    Text(
                      'Workouts Today',
                      style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                    ),
                  ],
                ),
                ElevatedButton.icon(
                  onPressed: () async {
                    final created = await Navigator.push(
                      context,
                      MaterialPageRoute(builder: (_) => const WorkoutFormScreen()),
                    );
                    if (created == true) _loadAllData();
                  },
                  icon: const Icon(Icons.add, size: 16),
                  label: const Text('Log workout'),
                  style: ElevatedButton.styleFrom(visualDensity: VisualDensity.compact),
                ),
              ],
            ),
            const SizedBox(height: 12),
            if (_dayWorkouts.isEmpty)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 12.0),
                child: Text('No workouts logged for this day.', style: TextStyle(color: Colors.white60)),
              )
            else
              ..._dayWorkouts.map((w) {
                final calText = w.caloriesBurnedEst != null ? ' · ~${w.caloriesBurnedEst} kcal' : '';
                return ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: const CircleAvatar(
                    radius: 18,
                    child: Icon(Icons.sports_gymnastics, size: 18),
                  ),
                  title: Text(w.name, style: const TextStyle(fontWeight: FontWeight.bold)),
                  subtitle: Text('${w.durationMin} min ($w.intensity)$calText'),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () async {
                    final updated = await Navigator.push(
                      context,
                      MaterialPageRoute(builder: (_) => WorkoutFormScreen(existingWorkout: w)),
                    );
                    if (updated == true) _loadAllData();
                  },
                );
              }),
          ],
        ),
      ),
    );
  }
}

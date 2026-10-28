import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import '../core/api_client.dart';
import '../exercises/exercise_library_screen.dart';
import 'fitness_repository.dart';
import 'models.dart';

class WorkoutFormScreen extends StatefulWidget {
  final Workout? existingWorkout;
  final dynamic initialExercise; // ExerciseDetail or ExerciseSummary

  const WorkoutFormScreen({
    super.key,
    this.existingWorkout,
    this.initialExercise,
  });

  @override
  State<WorkoutFormScreen> createState() => _WorkoutFormScreenState();
}

class _WorkoutEntryDraft {
  String? exerciseId;
  String exerciseName;
  String? thumbnailUrl;
  final setsController = TextEditingController(text: '3');
  final repsController = TextEditingController(text: '10');
  final durationController = TextEditingController();
  final weightController = TextEditingController();

  _WorkoutEntryDraft({
    this.exerciseId,
    required this.exerciseName,
    this.thumbnailUrl,
    int sets = 3,
    int? reps = 10,
    int? durationSeconds,
    double? weightKg,
  }) {
    setsController.text = sets.toString();
    if (reps != null) repsController.text = reps.toString();
    if (durationSeconds != null) durationController.text = durationSeconds.toString();
    if (weightKg != null) weightController.text = weightKg.toString();
  }

  void dispose() {
    setsController.dispose();
    repsController.dispose();
    durationController.dispose();
    weightController.dispose();
  }
}

class _WorkoutFormScreenState extends State<WorkoutFormScreen> {
  final _nameController = TextEditingController(text: 'Daily Workout');
  final _durationController = TextEditingController(text: '30');
  final _notesController = TextEditingController();
  DateTime _selectedDate = DateTime.now();
  String _selectedIntensity = 'moderate';

  final List<_WorkoutEntryDraft> _entries = [];
  bool _isSaving = false;
  String? _errorMessage;

  @override
  void initState() {
    super.initState();

    if (widget.existingWorkout != null) {
      final w = widget.existingWorkout!;
      _nameController.text = w.name;
      _durationController.text = w.durationMin.toString();
      _selectedIntensity = w.intensity;
      if (w.notes != null) _notesController.text = w.notes!;
      _selectedDate = DateTime.tryParse(w.date) ?? DateTime.now();

      for (final e in w.entries) {
        _entries.add(_WorkoutEntryDraft(
          exerciseId: e.exerciseId,
          exerciseName: e.exerciseName,
          thumbnailUrl: e.thumbnailUrl,
          sets: e.sets,
          reps: e.reps,
          durationSeconds: e.durationSeconds,
          weightKg: e.weightKg,
        ));
      }
    } else if (widget.initialExercise != null) {
      final dynamic ex = widget.initialExercise;
      String? thumb;
      if (ex is ExerciseDetail && ex.imageUrls.isNotEmpty) {
        thumb = ex.imageUrls.first;
      } else if (ex is ExerciseSummary) {
        thumb = ex.thumbnailUrl;
      }
      _entries.add(_WorkoutEntryDraft(
        exerciseId: ex.id,
        exerciseName: ex.name,
        thumbnailUrl: thumb,
        sets: 3,
        reps: 10,
      ));
    }
  }

  @override
  void dispose() {
    _nameController.dispose();
    _durationController.dispose();
    _notesController.dispose();
    for (final e in _entries) {
      e.dispose();
    }
    super.dispose();
  }

  void _addFromLibrary() async {
    final result = await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => const ExerciseLibraryScreen(pickMode: true),
      ),
    );

    if (result != null && mounted) {
      String id;
      String name;
      String? thumb;

      if (result is ExerciseDetail) {
        id = result.id;
        name = result.name;
        if (result.imageUrls.isNotEmpty) thumb = result.imageUrls.first;
      } else if (result is ExerciseSummary) {
        id = result.id;
        name = result.name;
        thumb = result.thumbnailUrl;
      } else {
        return;
      }

      setState(() {
        _entries.add(_WorkoutEntryDraft(
          exerciseId: id,
          exerciseName: name,
          thumbnailUrl: thumb,
          sets: 3,
          reps: 10,
        ));
      });
    }
  }

  void _addCustomExercise() async {
    final customController = TextEditingController();
    final name = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Add Custom Exercise'),
        content: TextField(
          controller: customController,
          autofocus: true,
          decoration: const InputDecoration(
            labelText: 'Exercise name',
            hintText: 'e.g. Jumping Jacks',
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () {
              final text = customController.text.trim();
              if (text.isNotEmpty) Navigator.pop(ctx, text);
            },
            child: const Text('Add'),
          ),
        ],
      ),
    );

    if (name != null && name.isNotEmpty && mounted) {
      setState(() {
        _entries.add(_WorkoutEntryDraft(
          exerciseId: null,
          exerciseName: name,
          sets: 3,
          reps: 10,
        ));
      });
    }
  }

  void _moveEntry(int from, int to) {
    if (to < 0 || to >= _entries.length) return;
    setState(() {
      final item = _entries.removeAt(from);
      _entries.insert(to, item);
    });
  }

  void _removeEntry(int index) {
    setState(() {
      final removed = _entries.removeAt(index);
      removed.dispose();
    });
  }

  Future<void> _pickDate() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _selectedDate,
      firstDate: DateTime.now().subtract(const Duration(days: 60)),
      lastDate: DateTime.now(),
    );
    if (picked != null && mounted) {
      setState(() {
        _selectedDate = picked;
      });
    }
  }

  Future<void> _saveWorkout() async {
    final name = _nameController.text.trim();
    final duration = int.tryParse(_durationController.text.trim());

    if (name.isEmpty) {
      setState(() => _errorMessage = 'Please enter a workout name');
      return;
    }
    if (duration == null || duration < 1 || duration > 600) {
      setState(() => _errorMessage = 'Duration must be between 1 and 600 minutes');
      return;
    }
    if (_entries.isEmpty) {
      setState(() => _errorMessage = 'Please add at least one exercise entry');
      return;
    }

    final entryPayloads = <Map<String, dynamic>>[];
    for (int i = 0; i < _entries.length; i++) {
      final e = _entries[i];
      final sets = int.tryParse(e.setsController.text.trim());
      final reps = int.tryParse(e.repsController.text.trim());
      final durSec = int.tryParse(e.durationController.text.trim());
      final weight = double.tryParse(e.weightController.text.trim());

      if (sets == null || sets < 1 || sets > 50) {
        setState(() => _errorMessage = 'Entry ${i + 1}: sets must be between 1 and 50');
        return;
      }
      if (reps == null && durSec == null) {
        setState(() => _errorMessage = 'Entry ${i + 1}: enter either reps or duration (seconds)');
        return;
      }

      final payload = <String, dynamic>{
        'sets': sets,
      };
      if (e.exerciseId != null) {
        payload['exerciseId'] = e.exerciseId;
      } else {
        payload['customName'] = e.exerciseName;
      }
      if (reps != null) payload['reps'] = reps;
      if (durSec != null) payload['durationSeconds'] = durSec;
      if (weight != null) payload['weightKg'] = weight;

      entryPayloads.add(payload);
    }

    setState(() {
      _isSaving = true;
      _errorMessage = null;
    });

    final repo = FitnessRepository(context.read<ApiClient>());
    final body = <String, dynamic>{
      'date': DateFormat('yyyy-MM-dd').format(_selectedDate),
      'name': name,
      'durationMin': duration,
      'intensity': _selectedIntensity,
      'entries': entryPayloads,
    };
    if (_notesController.text.trim().isNotEmpty) {
      body['notes'] = _notesController.text.trim();
    }

    try {
      Workout result;
      if (widget.existingWorkout != null) {
        result = await repo.updateWorkout(widget.existingWorkout!.id, body);
      } else {
        result = await repo.createWorkout(body);
      }

      if (mounted) {
        final message = result.caloriesBurnedEst != null
            ? 'Saved. Calories burned (estimate): ${result.caloriesBurnedEst}'
            : 'Saved. Add your weight in the profile to see a calorie estimate.';

        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
        Navigator.pop(context, true);
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _errorMessage = e.toString().replaceFirst('Exception: ', '');
          _isSaving = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final isEdit = widget.existingWorkout != null;

    return Scaffold(
      appBar: AppBar(
        title: Text(isEdit ? 'Edit Workout' : 'Log Workout'),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (_errorMessage != null) ...[
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.red.shade900.withOpacity(0.3),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: Colors.red.shade700),
                ),
                child: Text(_errorMessage!, style: const TextStyle(color: Colors.redAccent)),
              ),
              const SizedBox(height: 16),
            ],

            // Name
            TextField(
              controller: _nameController,
              decoration: const InputDecoration(
                labelText: 'Workout name',
                prefixIcon: Icon(Icons.fitness_center),
              ),
            ),
            const SizedBox(height: 16),

            // Date & Duration row
            Row(
              children: [
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: _pickDate,
                    icon: const Icon(Icons.calendar_today, size: 18),
                    label: Text(DateFormat('yyyy-MM-dd').format(_selectedDate)),
                    style: OutlinedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(vertical: 14),
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: TextField(
                    controller: _durationController,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(
                      labelText: 'Duration (min)',
                      prefixIcon: Icon(Icons.timer),
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),

            // Intensity Dropdown
            DropdownButtonFormField<String>(
              value: _selectedIntensity,
              decoration: const InputDecoration(
                labelText: 'Intensity',
                prefixIcon: Icon(Icons.bolt),
              ),
              items: const [
                DropdownMenuItem(value: 'light', child: Text('Light')),
                DropdownMenuItem(value: 'moderate', child: Text('Moderate')),
                DropdownMenuItem(value: 'vigorous', child: Text('Vigorous')),
              ],
              onChanged: (val) {
                if (val != null) setState(() => _selectedIntensity = val);
              },
            ),
            const SizedBox(height: 16),

            // Notes
            TextField(
              controller: _notesController,
              maxLines: 2,
              decoration: const InputDecoration(
                labelText: 'Notes (optional)',
                prefixIcon: Icon(Icons.note),
              ),
            ),
            const SizedBox(height: 24),

            // Entries section header
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  'Exercises (${_entries.length})',
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
                ),
                Row(
                  children: [
                    TextButton.icon(
                      onPressed: _addFromLibrary,
                      icon: const Icon(Icons.library_books, size: 18),
                      label: const Text('From library'),
                    ),
                    TextButton.icon(
                      onPressed: _addCustomExercise,
                      icon: const Icon(Icons.add, size: 18),
                      label: const Text('Custom'),
                    ),
                  ],
                ),
              ],
            ),
            const SizedBox(height: 8),

            // Entries list
            if (_entries.isEmpty)
              Container(
                padding: const EdgeInsets.all(24),
                alignment: Alignment.center,
                decoration: BoxDecoration(
                  border: Border.all(color: Colors.white24),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: const Text('No exercises added yet. Add from library or create custom.'),
              )
            else
              ...List.generate(_entries.length, (index) {
                final entry = _entries[index];
                return Card(
                  margin: const EdgeInsets.only(bottom: 12),
                  child: Padding(
                    padding: const EdgeInsets.all(12.0),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            if (entry.thumbnailUrl != null)
                              ClipRRect(
                                borderRadius: BorderRadius.circular(4),
                                child: Image.network(
                                  entry.thumbnailUrl!,
                                  width: 40,
                                  height: 40,
                                  fit: BoxFit.cover,
                                  errorBuilder: (_, __, ___) => const Icon(Icons.fitness_center),
                                ),
                              )
                            else
                              const CircleAvatar(
                                radius: 18,
                                child: Icon(Icons.fitness_center, size: 18),
                              ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: Text(
                                entry.exerciseName,
                                style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                              ),
                            ),
                            IconButton(
                              icon: const Icon(Icons.arrow_upward, size: 18),
                              tooltip: 'Move up',
                              onPressed: index > 0 ? () => _moveEntry(index, index - 1) : null,
                            ),
                            IconButton(
                              icon: const Icon(Icons.arrow_downward, size: 18),
                              tooltip: 'Move down',
                              onPressed: index < _entries.length - 1 ? () => _moveEntry(index, index + 1) : null,
                            ),
                            IconButton(
                              icon: const Icon(Icons.delete_outline, color: Colors.redAccent, size: 20),
                              tooltip: 'Delete',
                              onPressed: () => _removeEntry(index),
                            ),
                          ],
                        ),
                        const SizedBox(height: 12),
                        Row(
                          children: [
                            Expanded(
                              child: TextField(
                                controller: entry.setsController,
                                keyboardType: TextInputType.number,
                                decoration: const InputDecoration(labelText: 'Sets', isDense: true),
                              ),
                            ),
                            const SizedBox(width: 8),
                            Expanded(
                              child: TextField(
                                controller: entry.repsController,
                                keyboardType: TextInputType.number,
                                decoration: const InputDecoration(labelText: 'Reps', isDense: true),
                              ),
                            ),
                            const SizedBox(width: 8),
                            Expanded(
                              child: TextField(
                                controller: entry.durationController,
                                keyboardType: TextInputType.number,
                                decoration: const InputDecoration(labelText: 'Sec', isDense: true),
                              ),
                            ),
                            const SizedBox(width: 8),
                            Expanded(
                              child: TextField(
                                controller: entry.weightController,
                                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                                decoration: const InputDecoration(labelText: 'Kg', isDense: true),
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                );
              }),

            const SizedBox(height: 24),
            ElevatedButton(
              onPressed: _isSaving ? null : _saveWorkout,
              style: ElevatedButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 16)),
              child: _isSaving
                  ? const SizedBox(width: 24, height: 24, child: CircularProgressIndicator(strokeWidth: 2))
                  : Text(isEdit ? 'Update Workout' : 'Save Workout'),
            ),
          ],
        ),
      ),
    );
  }
}

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../core/api_client.dart';
import 'fitness_repository.dart';
import 'models.dart';

class GoalsScreen extends StatefulWidget {
  const GoalsScreen({super.key});

  @override
  State<GoalsScreen> createState() => _GoalsScreenState();
}

class _GoalsScreenState extends State<GoalsScreen> {
  final _stepsController = TextEditingController();
  final _waterController = TextEditingController();
  final _calorieController = TextEditingController();

  bool _isLoading = true;
  bool _isSaving = false;
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    _loadGoals();
  }

  @override
  void dispose() {
    _stepsController.dispose();
    _waterController.dispose();
    _calorieController.dispose();
    super.dispose();
  }

  Future<void> _loadGoals() async {
    final repo = FitnessRepository(context.read<ApiClient>());
    try {
      final goals = await repo.getGoals();
      if (mounted) {
        setState(() {
          _stepsController.text = goals.stepsGoal.toString();
          _waterController.text = goals.waterGoalMl.toString();
          if (goals.calorieGoal != null) {
            _calorieController.text = goals.calorieGoal.toString();
          }
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

  Future<void> _saveGoals() async {
    final steps = int.tryParse(_stepsController.text.trim());
    final water = int.tryParse(_waterController.text.trim());
    final calorieText = _calorieController.text.trim();
    final calorie = calorieText.isEmpty ? null : int.tryParse(calorieText);

    if (steps == null) {
      setState(() => _errorMessage = 'Please enter a valid number for steps goal');
      return;
    }
    if (water == null) {
      setState(() => _errorMessage = 'Please enter a valid number for water goal');
      return;
    }
    if (calorieText.isNotEmpty && calorie == null) {
      setState(() => _errorMessage = 'Please enter a valid number for calorie goal');
      return;
    }

    setState(() {
      _isSaving = true;
      _errorMessage = null;
    });

    final repo = FitnessRepository(context.read<ApiClient>());
    try {
      await repo.putGoals(
        stepsGoal: steps,
        waterGoalMl: water,
        calorieGoal: calorie,
      );
      if (mounted) {
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
    return Scaffold(
      appBar: AppBar(
        title: const Text('Fitness Goals'),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : SingleChildScrollView(
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
                      child: Text(
                        _errorMessage!,
                        style: const TextStyle(color: Colors.redAccent),
                      ),
                    ),
                    const SizedBox(height: 16),
                  ],
                  TextField(
                    controller: _stepsController,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(
                      labelText: 'Daily steps goal',
                      helperText: 'Between 100 and 100,000 steps',
                      prefixIcon: Icon(Icons.directions_walk),
                    ),
                  ),
                  const SizedBox(height: 20),
                  TextField(
                    controller: _waterController,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(
                      labelText: 'Daily water goal (ml)',
                      helperText: 'Between 250 and 10,000 ml',
                      prefixIcon: Icon(Icons.local_drink),
                    ),
                  ),
                  const SizedBox(height: 20),
                  TextField(
                    controller: _calorieController,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(
                      labelText: 'Daily calorie goal (optional)',
                      helperText: 'Between 500 and 10,000 kcal or empty',
                      prefixIcon: Icon(Icons.restaurant),
                    ),
                  ),
                  const SizedBox(height: 32),
                  ElevatedButton(
                    onPressed: _isSaving ? null : _saveGoals,
                    style: ElevatedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(vertical: 16),
                    ),
                    child: _isSaving
                        ? const SizedBox(
                            width: 24,
                            height: 24,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Text('Save Goals'),
                  ),
                ],
              ),
            ),
    );
  }
}

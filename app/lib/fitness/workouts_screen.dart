import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../core/api_client.dart';
import '../core/widgets/empty_state_widget.dart';
import '../core/widgets/loading_state_widget.dart';
import 'fitness_repository.dart';
import 'models.dart';
import 'workout_form_screen.dart';

class WorkoutsScreen extends StatefulWidget {
  const WorkoutsScreen({super.key});

  @override
  State<WorkoutsScreen> createState() => _WorkoutsScreenState();
}

class _WorkoutsScreenState extends State<WorkoutsScreen> {
  final _scrollController = ScrollController();
  bool _isLoadingInitial = true;
  bool _isLoadingMore = false;
  String? _errorMessage;

  List<Workout> _workouts = [];
  int _currentPage = 0;
  final int _pageSize = 20;
  int _total = 0;
  bool _hasMore = true;

  @override
  void initState() {
    super.initState();
    _loadWorkouts(reset: true);
    _scrollController.addListener(_onScroll);
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  void _onScroll() {
    if (_scrollController.position.pixels >= _scrollController.position.maxScrollExtent - 200) {
      if (!_isLoadingMore && _hasMore) {
        _loadWorkouts(reset: false);
      }
    }
  }

  Future<void> _loadWorkouts({required bool reset}) async {
    if (reset) {
      setState(() {
        _isLoadingInitial = true;
        _errorMessage = null;
        _currentPage = 0;
        _workouts.clear();
        _hasMore = true;
      });
    } else {
      setState(() {
        _isLoadingMore = true;
      });
    }

    final repo = FitnessRepository(context.read<ApiClient>());
    try {
      final res = await repo.getWorkouts(
        page: _currentPage,
        size: _pageSize,
      );

      final List<Workout> items = res['items'] as List<Workout>;
      final num totalCount = res['total'] as num? ?? 0;

      if (mounted) {
        setState(() {
          if (reset) {
            _workouts = items;
          } else {
            _workouts.addAll(items);
          }
          _total = totalCount.toInt();
          _currentPage++;
          _hasMore = _workouts.length < _total;
          _isLoadingInitial = false;
          _isLoadingMore = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _errorMessage = e.toString().replaceFirst('Exception: ', '');
          _isLoadingInitial = false;
          _isLoadingMore = false;
        });
      }
    }
  }

  void _openWorkout(Workout w) async {
    final updated = await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (ctx) => Scaffold(
          appBar: AppBar(
            title: const Text('Workout Details'),
            actions: [
              IconButton(
                icon: const Icon(Icons.delete_outline, color: Colors.redAccent),
                tooltip: 'Delete Workout',
                onPressed: () async {
                  final confirm = await showDialog<bool>(
                    context: ctx,
                    builder: (dCtx) => AlertDialog(
                      title: const Text('Delete Workout'),
                      content: const Text('Are you sure you want to delete this workout?'),
                      actions: [
                        TextButton(
                          onPressed: () => Navigator.pop(dCtx, false),
                          child: const Text('Cancel'),
                        ),
                        ElevatedButton(
                          style: ElevatedButton.styleFrom(backgroundColor: Colors.red),
                          onPressed: () => Navigator.pop(dCtx, true),
                          child: const Text('Delete'),
                        ),
                      ],
                    ),
                  );

                  if (confirm == true && mounted) {
                    final repo = FitnessRepository(context.read<ApiClient>());
                    try {
                      await repo.deleteWorkout(w.id);
                      if (ctx.mounted) {
                        Navigator.pop(ctx, true);
                      }
                    } catch (e) {
                      if (ctx.mounted) {
                        ScaffoldMessenger.of(ctx).showSnackBar(
                          SnackBar(content: Text('Failed to delete: $e')),
                        );
                      }
                    }
                  }
                },
              ),
            ],
          ),
          body: WorkoutFormScreen(existingWorkout: w),
        ),
      ),
    );

    if (updated == true && mounted) {
      _loadWorkouts(reset: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Workout History'),
        actions: [
          IconButton(
            icon: const Icon(Icons.add),
            tooltip: 'Log workout',
            onPressed: () async {
              final created = await Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const WorkoutFormScreen()),
              );
              if (created == true && mounted) {
                _loadWorkouts(reset: true);
              }
            },
          ),
        ],
      ),
      body: _isLoadingInitial
          ? const LoadingStateWidget()
          : _errorMessage != null
              ? ErrorStateWidget(
                  message: _errorMessage,
                  onRetry: () => _loadWorkouts(reset: true),
                )
              : _workouts.isEmpty
                  ? EmptyStateWidget(
                      icon: Icons.fitness_center_outlined,
                      message: 'No workouts logged yet',
                      actionLabel: 'Log Workout',
                      onAction: () async {
                        final created = await Navigator.push(
                          context,
                          MaterialPageRoute(builder: (_) => const WorkoutFormScreen()),
                        );
                        if (created == true && mounted) {
                          _loadWorkouts(reset: true);
                        }
                      },
                    )
                  : RefreshIndicator(
                      onRefresh: () => _loadWorkouts(reset: true),
                      child: ListView.separated(
                        controller: _scrollController,
                        itemCount: _workouts.length + (_hasMore ? 1 : 0),
                        separatorBuilder: (_, __) => const Divider(height: 1),
                        itemBuilder: (context, index) {
                          if (index == _workouts.length) {
                            return const Padding(
                              padding: EdgeInsets.all(16.0),
                              child: Center(child: CircularProgressIndicator()),
                            );
                          }
                          final w = _workouts[index];
                          final calText = w.caloriesBurnedEst != null
                              ? ' · ~${w.caloriesBurnedEst} kcal'
                              : '';

                          return ListTile(
                            leading: CircleAvatar(
                              child: Icon(
                                w.intensity == 'vigorous'
                                    ? Icons.bolt
                                    : w.intensity == 'moderate'
                                        ? Icons.fitness_center
                                        : Icons.nature_people,
                              ),
                            ),
                            title: Text(w.name, style: const TextStyle(fontWeight: FontWeight.bold)),
                            subtitle: Text('${w.date} · ${w.durationMin} min ($w.intensity)$calText'),
                            trailing: const Icon(Icons.chevron_right),
                            onTap: () => _openWorkout(w),
                          );
                        },
                      ),
                    ),
    );
  }
}

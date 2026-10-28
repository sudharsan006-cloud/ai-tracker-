import 'dart:async';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../core/api_client.dart';
import '../fitness/fitness_repository.dart';
import '../fitness/models.dart';
import '../fitness/workout_form_screen.dart';

class ExerciseDetailScreen extends StatefulWidget {
  final String exerciseId;
  final bool pickMode;

  const ExerciseDetailScreen({
    super.key,
    required this.exerciseId,
    this.pickMode = false,
  });

  @override
  State<ExerciseDetailScreen> createState() => _ExerciseDetailScreenState();
}

class _ExerciseDetailScreenState extends State<ExerciseDetailScreen> {
  bool _isLoading = true;
  String? _errorMessage;
  ExerciseDetail? _detail;

  Timer? _animationTimer;
  int _currentImageIndex = 0;
  bool _isAnimationPaused = false;

  @override
  void initState() {
    super.initState();
    _loadDetail();
  }

  @override
  void dispose() {
    _animationTimer?.cancel();
    super.dispose();
  }

  Future<void> _loadDetail() async {
    final repo = FitnessRepository(context.read<ApiClient>());
    try {
      final detail = await repo.getExercise(widget.exerciseId);
      if (mounted) {
        setState(() {
          _detail = detail;
          _isLoading = false;
        });
        _setupImageAnimation();
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

  void _setupImageAnimation() {
    _animationTimer?.cancel();
    if (_detail != null && _detail!.imageUrls.length > 1) {
      _animationTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
        if (!_isAnimationPaused && mounted) {
          setState(() {
            _currentImageIndex = (_currentImageIndex + 1) % _detail!.imageUrls.length;
          });
        }
      });
    }
  }

  void _toggleFavorite() async {
    if (_detail == null) return;
    final repo = FitnessRepository(context.read<ApiClient>());
    final newFav = !_detail!.isFavorite;
    setState(() {
      _detail!.isFavorite = newFav;
    });

    try {
      if (newFav) {
        await repo.favoriteExercise(_detail!.id);
      } else {
        await repo.unfavoriteExercise(_detail!.id);
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _detail!.isFavorite = !newFav;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(_detail?.name ?? 'Exercise Detail'),
        actions: [
          if (_detail != null)
            IconButton(
              icon: Icon(
                _detail!.isFavorite ? Icons.star : Icons.star_border,
                color: _detail!.isFavorite ? Colors.amber : null,
              ),
              tooltip: _detail!.isFavorite ? 'Unfavorite' : 'Favorite',
              onPressed: _toggleFavorite,
            ),
        ],
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : _errorMessage != null
              ? Center(child: Text(_errorMessage!, style: const TextStyle(color: Colors.redAccent)))
              : _detail == null
                  ? const Center(child: Text('Exercise not found'))
                  : _buildContent(),
      bottomNavigationBar: _detail != null
          ? SafeArea(
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: ElevatedButton.icon(
                  onPressed: () {
                    if (widget.pickMode) {
                      Navigator.pop(context, _detail);
                    } else {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => WorkoutFormScreen(
                            initialExercise: _detail,
                          ),
                        ),
                      );
                    }
                  },
                  icon: const Icon(Icons.add),
                  label: const Text('Add to workout'),
                  style: ElevatedButton.styleFrom(
                    padding: const EdgeInsets.symmetric(vertical: 14),
                  ),
                ),
              ),
            )
          : null,
    );
  }

  Widget _buildContent() {
    final d = _detail!;
    final images = d.imageUrls;

    return SingleChildScrollView(
      padding: const EdgeInsets.all(16.0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 4:3 Image Area
          if (images.isNotEmpty) ...[
            AspectRatio(
              aspectRatio: 4 / 3,
              child: GestureDetector(
                onTap: () {
                  setState(() {
                    _isAnimationPaused = false;
                  });
                },
                child: Container(
                  decoration: BoxDecoration(
                    color: Colors.black26,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  clipBehavior: Clip.antiAlias,
                  child: AnimatedSwitcher(
                    duration: const Duration(milliseconds: 300),
                    child: Image.network(
                      images[_currentImageIndex],
                      key: ValueKey<String>(images[_currentImageIndex]),
                      fit: BoxFit.contain,
                      width: double.infinity,
                      height: double.infinity,
                      loadingBuilder: (context, child, progress) {
                        if (progress == null) return child;
                        return const Center(child: CircularProgressIndicator());
                      },
                      errorBuilder: (context, error, stackTrace) {
                        return const Center(
                          child: Icon(Icons.image_not_supported, size: 48, color: Colors.white54),
                        );
                      },
                    ),
                  ),
                ),
              ),
            ),
            if (images.length > 1) ...[
              const SizedBox(height: 8),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: List.generate(images.length, (index) {
                  final isSelected = index == _currentImageIndex;
                  return GestureDetector(
                    onTap: () {
                      setState(() {
                        _currentImageIndex = index;
                        _isAnimationPaused = true;
                      });
                    },
                    child: Container(
                      margin: const EdgeInsets.symmetric(horizontal: 6),
                      width: 50,
                      height: 50,
                      decoration: BoxDecoration(
                        border: Border.all(
                          color: isSelected ? Theme.of(context).colorScheme.primary : Colors.transparent,
                          width: 2,
                        ),
                        borderRadius: BorderRadius.circular(6),
                      ),
                      clipBehavior: Clip.antiAlias,
                      child: Image.network(
                        images[index],
                        fit: BoxFit.cover,
                        errorBuilder: (_, __, ___) => const Icon(Icons.image_not_supported, size: 20),
                      ),
                    ),
                  );
                }),
              ),
            ],
            const SizedBox(height: 16),
          ],

          // Title
          Text(
            d.name,
            style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 12),

          // Chips
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              if (d.level != null && d.level!.isNotEmpty)
                Chip(label: Text(d.level!), backgroundColor: Colors.blueGrey.shade800),
              Chip(label: Text(d.equipment), backgroundColor: Colors.teal.shade800),
              Chip(label: Text(d.category), backgroundColor: Colors.indigo.shade800),
              ...d.primaryMuscles.map(
                (m) => Chip(
                  label: Text(m),
                  avatar: const Icon(Icons.fitness_center, size: 16),
                ),
              ),
            ],
          ),

          if (d.secondaryMuscles.isNotEmpty) ...[
            const SizedBox(height: 12),
            Text(
              'Secondary muscles: ${d.secondaryMuscles.join(', ')}',
              style: const TextStyle(color: Colors.white70, fontSize: 13),
            ),
          ],

          const Divider(height: 32),

          // Instructions
          Text(
            'Instructions',
            style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 8),
          if (d.instructions.isEmpty)
            const Text('No instructions available.', style: TextStyle(color: Colors.white60))
          else
            ...List.generate(d.instructions.length, (idx) {
              return Padding(
                padding: const EdgeInsets.only(bottom: 8.0),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '${idx + 1}. ',
                      style: TextStyle(
                        fontWeight: FontWeight.bold,
                        color: Theme.of(context).colorScheme.primary,
                      ),
                    ),
                    Expanded(
                      child: Text(
                        d.instructions[idx],
                        style: const TextStyle(height: 1.4),
                      ),
                    ),
                  ],
                ),
              );
            }),

          const SizedBox(height: 24),
          const Center(
            child: Text(
              'Exercise data: free-exercise-db (public domain)',
              style: TextStyle(fontSize: 12, color: Colors.white38),
            ),
          ),
          const SizedBox(height: 16),
        ],
      ),
    );
  }
}

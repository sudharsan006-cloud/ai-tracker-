import 'dart:async';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../core/api_client.dart';
import '../fitness/fitness_repository.dart';
import '../fitness/models.dart';
import 'exercise_detail_screen.dart';

class ExerciseLibraryScreen extends StatefulWidget {
  final bool pickMode;

  const ExerciseLibraryScreen({super.key, this.pickMode = false});

  @override
  State<ExerciseLibraryScreen> createState() => _ExerciseLibraryScreenState();
}

class _ExerciseLibraryScreenState extends State<ExerciseLibraryScreen> {
  final _searchController = TextEditingController();
  final _scrollController = ScrollController();
  Timer? _debounceTimer;

  bool _isLoadingInitial = true;
  bool _isLoadingMore = false;
  String? _errorMessage;

  List<ExerciseSummary> _exercises = [];
  int _currentPage = 0;
  final int _pageSize = 30;
  int _total = 0;
  bool _hasMore = true;

  // Filters
  ExerciseFilters? _filters;
  String? _selectedMuscle;
  String? _selectedEquipment;
  String? _selectedLevel;
  String? _selectedCategory;
  bool _favoritesOnly = false;

  @override
  void initState() {
    super.initState();
    _loadFilters();
    _loadExercises(reset: true);
    _scrollController.addListener(_onScroll);
  }

  @override
  void dispose() {
    _searchController.dispose();
    _scrollController.dispose();
    _debounceTimer?.cancel();
    super.dispose();
  }

  void _onScroll() {
    if (_scrollController.position.pixels >= _scrollController.position.maxScrollExtent - 200) {
      if (!_isLoadingMore && _hasMore) {
        _loadExercises(reset: false);
      }
    }
  }

  void _onSearchChanged(String value) {
    _debounceTimer?.cancel();
    _debounceTimer = Timer(const Duration(milliseconds: 400), () {
      _loadExercises(reset: true);
    });
  }

  Future<void> _loadFilters() async {
    final repo = FitnessRepository(context.read<ApiClient>());
    try {
      final filters = await repo.getExerciseFilters();
      if (mounted) {
        setState(() {
          _filters = filters;
        });
      }
    } catch (_) {
      // Non-critical, filters dropdowns will just be empty or defaults
    }
  }

  Future<void> _loadExercises({required bool reset}) async {
    if (reset) {
      setState(() {
        _isLoadingInitial = true;
        _errorMessage = null;
        _currentPage = 0;
        _exercises.clear();
        _hasMore = true;
      });
    } else {
      setState(() {
        _isLoadingMore = true;
      });
    }

    final repo = FitnessRepository(context.read<ApiClient>());
    try {
      final res = await repo.getExercises(
        q: _searchController.text.trim().isEmpty ? null : _searchController.text.trim(),
        muscle: _selectedMuscle,
        equipment: _selectedEquipment,
        level: _selectedLevel,
        category: _selectedCategory,
        favoritesOnly: _favoritesOnly ? true : null,
        page: _currentPage,
        size: _pageSize,
      );

      final List<ExerciseSummary> items = res['items'] as List<ExerciseSummary>;
      final num totalCount = res['total'] as num? ?? 0;

      if (mounted) {
        setState(() {
          if (reset) {
            _exercises = items;
          } else {
            _exercises.addAll(items);
          }
          _total = totalCount.toInt();
          _currentPage++;
          _hasMore = _exercises.length < _total;
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

  void _toggleFavorite(ExerciseSummary ex) async {
    final repo = FitnessRepository(context.read<ApiClient>());
    final newFav = !ex.isFavorite;
    setState(() {
      ex.isFavorite = newFav;
    });

    try {
      if (newFav) {
        await repo.favoriteExercise(ex.id);
      } else {
        await repo.unfavoriteExercise(ex.id);
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          ex.isFavorite = !newFav;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.pickMode ? 'Select Exercise' : 'Exercise Library'),
      ),
      body: Column(
        children: [
          // Search & Filter header
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
            child: TextField(
              controller: _searchController,
              onChanged: _onSearchChanged,
              decoration: InputDecoration(
                hintText: 'Search exercises...',
                prefixIcon: const Icon(Icons.search),
                suffixIcon: _searchController.text.isNotEmpty
                    ? IconButton(
                        icon: const Icon(Icons.clear),
                        onPressed: () {
                          _searchController.clear();
                          _loadExercises(reset: true);
                        },
                      )
                    : null,
                isDense: true,
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
              ),
            ),
          ),

          // Horizontal filters row
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
            child: Row(
              children: [
                FilterChip(
                  label: const Text('Favorites'),
                  selected: _favoritesOnly,
                  avatar: Icon(
                    _favoritesOnly ? Icons.star : Icons.star_border,
                    size: 16,
                    color: _favoritesOnly ? Colors.amber : null,
                  ),
                  onSelected: (val) {
                    setState(() => _favoritesOnly = val);
                    _loadExercises(reset: true);
                  },
                ),
                const SizedBox(width: 8),
                _buildFilterDropdown(
                  hint: 'Muscle',
                  value: _selectedMuscle,
                  items: _filters?.muscles ?? [],
                  onChanged: (val) {
                    setState(() => _selectedMuscle = val);
                    _loadExercises(reset: true);
                  },
                ),
                const SizedBox(width: 8),
                _buildFilterDropdown(
                  hint: 'Equipment',
                  value: _selectedEquipment,
                  items: _filters?.equipment ?? [],
                  onChanged: (val) {
                    setState(() => _selectedEquipment = val);
                    _loadExercises(reset: true);
                  },
                ),
                const SizedBox(width: 8),
                _buildFilterDropdown(
                  hint: 'Level',
                  value: _selectedLevel,
                  items: _filters?.levels ?? [],
                  onChanged: (val) {
                    setState(() => _selectedLevel = val);
                    _loadExercises(reset: true);
                  },
                ),
                const SizedBox(width: 8),
                _buildFilterDropdown(
                  hint: 'Category',
                  value: _selectedCategory,
                  items: _filters?.categories ?? [],
                  onChanged: (val) {
                    setState(() => _selectedCategory = val);
                    _loadExercises(reset: true);
                  },
                ),
              ],
            ),
          ),

          const Divider(height: 1),

          // Exercise list
          Expanded(
            child: _isLoadingInitial
                ? const Center(child: CircularProgressIndicator())
                : _errorMessage != null
                    ? Center(child: Text(_errorMessage!, style: const TextStyle(color: Colors.redAccent)))
                    : _exercises.isEmpty
                        ? const Center(child: Text('No exercises found'))
                        : ListView.separated(
                            controller: _scrollController,
                            itemCount: _exercises.length + (_hasMore ? 1 : 0),
                            separatorBuilder: (_, __) => const Divider(height: 1),
                            itemBuilder: (context, index) {
                              if (index == _exercises.length) {
                                return const Padding(
                                  padding: EdgeInsets.all(16.0),
                                  child: Center(child: CircularProgressIndicator()),
                                );
                              }
                              final ex = _exercises[index];
                              final subline = [
                                if (ex.primaryMuscles.isNotEmpty) ex.primaryMuscles.join(', '),
                                ex.equipment,
                                if (ex.level != null && ex.level!.isNotEmpty) ex.level!,
                              ].join(' · ');

                              return ListTile(
                                leading: ClipRRect(
                                  borderRadius: BorderRadius.circular(6),
                                  child: ex.thumbnailUrl != null
                                      ? Image.network(
                                          ex.thumbnailUrl!,
                                          width: 64,
                                          height: 64,
                                          fit: BoxFit.cover,
                                          cacheWidth: 200,
                                          loadingBuilder: (context, child, progress) {
                                            if (progress == null) return child;
                                            return const SizedBox(
                                              width: 64,
                                              height: 64,
                                              child: Center(child: CircularProgressIndicator(strokeWidth: 2)),
                                            );
                                          },
                                          errorBuilder: (context, error, stackTrace) => Container(
                                            width: 64,
                                            height: 64,
                                            color: Colors.white10,
                                            child: const Icon(Icons.fitness_center),
                                          ),
                                        )
                                      : Container(
                                          width: 64,
                                          height: 64,
                                          color: Colors.white10,
                                          child: const Icon(Icons.fitness_center),
                                        ),
                                ),
                                title: Text(ex.name, style: const TextStyle(fontWeight: FontWeight.w600)),
                                subtitle: Text(
                                  subline,
                                  style: const TextStyle(fontSize: 12, color: Colors.white70),
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                ),
                                trailing: IconButton(
                                  icon: Icon(
                                    ex.isFavorite ? Icons.star : Icons.star_border,
                                    color: ex.isFavorite ? Colors.amber : null,
                                  ),
                                  tooltip: ex.isFavorite ? 'Unfavorite' : 'Favorite',
                                  onPressed: () => _toggleFavorite(ex),
                                ),
                                onTap: () async {
                                  final result = await Navigator.push(
                                    context,
                                    MaterialPageRoute(
                                      builder: (_) => ExerciseDetailScreen(
                                        exerciseId: ex.id,
                                        pickMode: widget.pickMode,
                                      ),
                                    ),
                                  );
                                  if (widget.pickMode && result != null && mounted) {
                                    Navigator.pop(context, result);
                                  }
                                },
                              );
                            },
                          ),
          ),
        ],
      ),
    );
  }

  Widget _buildFilterDropdown({
    required String hint,
    required String? value,
    required List<String> items,
    required ValueChanged<String?> onChanged,
  }) {
    return DropdownButton<String?>(
      value: value,
      hint: Text(hint, style: const TextStyle(fontSize: 13)),
      isDense: true,
      underline: const SizedBox.shrink(),
      items: [
        DropdownMenuItem<String?>(
          value: null,
          child: Text('All $hint', style: const TextStyle(fontSize: 13)),
        ),
        ...items.map(
          (item) => DropdownMenuItem<String?>(
            value: item,
            child: Text(item, style: const TextStyle(fontSize: 13)),
          ),
        ),
      ],
      onChanged: onChanged,
    );
  }
}

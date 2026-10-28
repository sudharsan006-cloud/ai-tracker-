import 'package:flutter/material.dart';
import 'insights_repository.dart';
import 'mood_history_screen.dart';

class MoodCard extends StatefulWidget {
  final InsightsRepository repository;
  final VoidCallback? onMoodSaved;

  const MoodCard({
    super.key,
    required this.repository,
    this.onMoodSaved,
  });

  @override
  State<MoodCard> createState() => _MoodCardState();
}

class _MoodCardState extends State<MoodCard> {
  int? _selectedScore;
  final TextEditingController _noteController = TextEditingController();
  bool _isLoading = true;
  bool _isSaving = false;

  static const List<IconData> _faces = [
    Icons.sentiment_very_dissatisfied,
    Icons.sentiment_dissatisfied,
    Icons.sentiment_neutral,
    Icons.sentiment_satisfied,
    Icons.sentiment_very_satisfied,
  ];

  static const List<String> _faceLabels = [
    'Very Low',
    'Low',
    'Neutral',
    'Good',
    'Great',
  ];

  static const List<Color> _faceColors = [
    Colors.redAccent,
    Colors.orangeAccent,
    Colors.amber,
    Colors.lightGreen,
    Colors.green,
  ];

  @override
  void initState() {
    super.initState();
    _loadTodayMood();
  }

  @override
  void dispose() {
    _noteController.dispose();
    super.dispose();
  }

  Future<void> _loadTodayMood() async {
    try {
      final history = await widget.repository.getMoodHistory();
      final now = DateTime.now();
      final todayStr = "${now.year}-${now.month.toString().padLeft(2, '0')}-${now.day.toString().padLeft(2, '0')}";

      final todayEntry = history.cast<dynamic>().firstWhere(
        (e) => e.date == todayStr,
        orElse: () => null,
      );

      if (mounted) {
        setState(() {
          if (todayEntry != null) {
            _selectedScore = todayEntry.score;
            _noteController.text = todayEntry.note ?? '';
          }
          _isLoading = false;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
      }
    }
  }

  Future<void> _saveMood() async {
    if (_selectedScore == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please select a mood score')),
      );
      return;
    }

    setState(() {
      _isSaving = true;
    });

    try {
      await widget.repository.putMood(
        score: _selectedScore!,
        note: _noteController.text,
      );

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Mood saved')),
        );
        widget.onMoodSaved?.call();
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to save mood: ${e.toString().replaceFirst('Exception: ', '')}')),
        );
      }
    } finally {
      if (mounted) {
        setState(() {
          _isSaving = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Card(
      elevation: 2,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(color: Colors.white.withOpacity(0.08)),
      ),
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  'How are you today?',
                  style: theme.textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
                ),
                TextButton.icon(
                  icon: const Icon(Icons.history, size: 18),
                  label: const Text('History'),
                  style: TextButton.styleFrom(
                    visualDensity: VisualDensity.compact,
                    foregroundColor: theme.colorScheme.primary,
                  ),
                  onPressed: () {
                    Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (_) => MoodHistoryScreen(repository: widget.repository),
                      ),
                    ).then((_) => _loadTodayMood());
                  },
                ),
              ],
            ),
            const SizedBox(height: 14),

            if (_isLoading)
              const Center(
                child: Padding(
                  padding: EdgeInsets.symmetric(vertical: 24),
                  child: CircularProgressIndicator(),
                ),
              )
            else ...[
              // Five face icons
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceAround,
                children: List.generate(5, (index) {
                  final score = index + 1;
                  final isSelected = _selectedScore == score;
                  final color = _faceColors[index];

                  return InkWell(
                    onTap: () {
                      setState(() {
                        _selectedScore = score;
                      });
                    },
                    borderRadius: BorderRadius.circular(12),
                    child: AnimatedContainer(
                      duration: const Duration(milliseconds: 200),
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                      decoration: BoxDecoration(
                        color: isSelected ? color.withOpacity(0.18) : Colors.transparent,
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(
                          color: isSelected ? color : Colors.transparent,
                          width: 2,
                        ),
                      ),
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(
                            _faces[index],
                            size: 34,
                            color: isSelected ? color : Colors.white54,
                          ),
                          const SizedBox(height: 4),
                          Text(
                            _faceLabels[index],
                            style: TextStyle(
                              fontSize: 10,
                              fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                              color: isSelected ? color : Colors.white54,
                            ),
                          ),
                        ],
                      ),
                    ),
                  );
                }),
              ),
              const SizedBox(height: 16),

              // Private note text field
              TextField(
                controller: _noteController,
                maxLength: 300,
                maxLines: 2,
                decoration: InputDecoration(
                  labelText: 'Private note, never sent to AI',
                  labelStyle: const TextStyle(fontSize: 13, color: Colors.white60),
                  hintText: 'Add an optional note about your day...',
                  hintStyle: const TextStyle(fontSize: 12, color: Colors.white30),
                  filled: true,
                  fillColor: Colors.white.withOpacity(0.04),
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(10),
                    borderSide: BorderSide(color: Colors.white.withOpacity(0.1)),
                  ),
                  enabledBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(10),
                    borderSide: BorderSide(color: Colors.white.withOpacity(0.1)),
                  ),
                  focusedBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(10),
                    borderSide: BorderSide(color: theme.colorScheme.primary),
                  ),
                ),
              ),
              const SizedBox(height: 8),

              // Save button
              Align(
                alignment: Alignment.centerRight,
                child: ElevatedButton.icon(
                  onPressed: _isSaving ? null : _saveMood,
                  icon: _isSaving
                      ? const SizedBox(
                          width: 16,
                          height: 16,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.check, size: 18),
                  label: const Text('Save'),
                  style: ElevatedButton.styleFrom(
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(10),
                    ),
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

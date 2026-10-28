import 'package:flutter/material.dart';
import '../core/widgets/empty_state_widget.dart';
import '../core/widgets/loading_state_widget.dart';
import 'insights_repository.dart';
import 'models.dart';

class MoodHistoryScreen extends StatefulWidget {
  final InsightsRepository repository;

  const MoodHistoryScreen({super.key, required this.repository});

  @override
  State<MoodHistoryScreen> createState() => _MoodHistoryScreenState();
}

class _MoodHistoryScreenState extends State<MoodHistoryScreen> {
  bool _isLoading = true;
  String? _errorMessage;
  List<MoodEntry> _entries = [];

  static const List<IconData> _scoreIcons = [
    Icons.sentiment_very_dissatisfied,
    Icons.sentiment_dissatisfied,
    Icons.sentiment_neutral,
    Icons.sentiment_satisfied,
    Icons.sentiment_very_satisfied,
  ];

  static const List<Color> _scoreColors = [
    Colors.redAccent,
    Colors.orangeAccent,
    Colors.amber,
    Colors.lightGreen,
    Colors.green,
  ];

  @override
  void initState() {
    super.initState();
    _loadHistory();
  }

  Future<void> _loadHistory() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final now = DateTime.now();
      final from = now.subtract(const Duration(days: 30));
      final fromStr = "${from.year}-${from.month.toString().padLeft(2, '0')}-${from.day.toString().padLeft(2, '0')}";
      final toStr = "${now.year}-${now.month.toString().padLeft(2, '0')}-${now.day.toString().padLeft(2, '0')}";

      final list = await widget.repository.getMoodHistory(from: fromStr, to: toStr);
      if (mounted) {
        setState(() {
          _entries = list;
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

  Future<void> _deleteEntry(String date) async {
    try {
      await widget.repository.deleteMood(date);
      setState(() {
        _entries.removeWhere((item) => item.date == date);
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Mood entry deleted')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to delete: ${e.toString().replaceFirst('Exception: ', '')}')),
        );
      }
    }
  }

  IconData _getIconForScore(int score) {
    if (score >= 1 && score <= 5) {
      return _scoreIcons[score - 1];
    }
    return Icons.sentiment_neutral;
  }

  Color _getColorForScore(int score) {
    if (score >= 1 && score <= 5) {
      return _scoreColors[score - 1];
    }
    return Colors.white70;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Mood History (30 Days)'),
      ),
      body: _isLoading
          ? const LoadingStateWidget()
          : _errorMessage != null
              ? ErrorStateWidget(
                  message: _errorMessage,
                  onRetry: _loadHistory,
                )
              : _entries.isEmpty
                  ? const EmptyStateWidget(
                      icon: Icons.sentiment_satisfied_alt_outlined,
                      message: 'No mood entries recorded in the last 30 days.',
                    )
                  : RefreshIndicator(
                      onRefresh: _loadHistory,
                      child: ListView.separated(
                      padding: const EdgeInsets.all(16),
                      itemCount: _entries.length,
                      separatorBuilder: (_, __) => const Divider(height: 1),
                      itemBuilder: (context, index) {
                        final entry = _entries[index];
                        return Dismissible(
                          key: Key(entry.date),
                          direction: DismissDirection.endToStart,
                          background: Container(
                            alignment: Alignment.centerRight,
                            padding: const EdgeInsets.symmetric(horizontal: 20),
                            color: Colors.red.shade900,
                            child: const Icon(Icons.delete, color: Colors.white),
                          ),
                          onDismissed: (_) => _deleteEntry(entry.date),
                          child: ListTile(
                            contentPadding: const EdgeInsets.symmetric(vertical: 4, horizontal: 8),
                            leading: Icon(
                              _getIconForScore(entry.score),
                              color: _getColorForScore(entry.score),
                              size: 32,
                            ),
                            title: Text(
                              entry.date,
                              style: const TextStyle(fontWeight: FontWeight.w600),
                            ),
                            subtitle: entry.note != null && entry.note!.isNotEmpty
                                ? Padding(
                                    padding: const EdgeInsets.only(top: 4.0),
                                    child: Text(
                                      entry.note!,
                                      style: const TextStyle(color: Colors.white70),
                                    ),
                                  )
                                : null,
                            trailing: IconButton(
                              icon: const Icon(Icons.delete_outline, color: Colors.white54),
                              tooltip: 'Delete',
                              onPressed: () => _deleteEntry(entry.date),
                            ),
                          ),
                        );
                      },
                    ),
                  ),
    );
  }
}

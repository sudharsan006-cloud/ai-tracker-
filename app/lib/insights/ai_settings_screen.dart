import 'package:flutter/material.dart';
import 'insights_repository.dart';
import 'models.dart';

class AiSettingsScreen extends StatefulWidget {
  final InsightsRepository repository;

  const AiSettingsScreen({super.key, required this.repository});

  @override
  State<AiSettingsScreen> createState() => _AiSettingsScreenState();
}

class _AiSettingsScreenState extends State<AiSettingsScreen> {
  bool _isLoading = true;
  bool _aiEnabled = true;
  bool _includeMood = false;

  static const String _explanationText =
      'Summaries are written by Google Gemini on a free plan. Only numbers calculated by this app are sent: totals, averages, category names and goals. Your messages, merchant names, account details, names and mood notes are never sent. Google may use inputs on the free plan to improve its products. Mood numbers are sent only if you switch on "Include my mood".';

  @override
  void initState() {
    super.initState();
    _loadSettings();
  }

  Future<void> _loadSettings() async {
    setState(() {
      _isLoading = true;
    });

    try {
      final s = await widget.repository.getAiSettings();
      if (mounted) {
        setState(() {
          _aiEnabled = s.aiEnabled;
          _includeMood = s.includeMood;
          _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to load settings: ${e.toString().replaceFirst('Exception: ', '')}')),
        );
      }
    }
  }

  Future<void> _saveSettings({bool? aiEnabled, bool? includeMood}) async {
    final newAiEnabled = aiEnabled ?? _aiEnabled;
    final newIncludeMood = includeMood ?? _includeMood;

    // Optimistically update
    setState(() {
      _aiEnabled = newAiEnabled;
      _includeMood = newIncludeMood;
    });

    try {
      final updated = await widget.repository.updateAiSettings(
        AiSettings(
          aiEnabled: newAiEnabled,
          includeMood: newIncludeMood,
        ),
      );
      if (mounted) {
        setState(() {
          _aiEnabled = updated.aiEnabled;
          _includeMood = updated.includeMood;
        });
      }
    } catch (e) {
      if (mounted) {
        // Revert on error
        _loadSettings();
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to save settings: ${e.toString().replaceFirst('Exception: ', '')}')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('AI Settings'),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                SwitchListTile(
                  title: const Text('Use AI summaries'),
                  subtitle: const Text('Allow generating summaries using Google Gemini'),
                  value: _aiEnabled,
                  onChanged: (val) {
                    _saveSettings(aiEnabled: val);
                  },
                ),
                const Divider(),
                SwitchListTile(
                  title: const Text('Include my mood in AI summaries'),
                  subtitle: const Text('Share aggregated mood counts and average score'),
                  value: _aiEnabled && _includeMood,
                  onChanged: _aiEnabled
                      ? (val) {
                          _saveSettings(includeMood: val);
                        }
                      : null,
                ),
                const SizedBox(height: 24),
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: Colors.white.withOpacity(0.04),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: Colors.white.withOpacity(0.08)),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Icon(Icons.privacy_tip_outlined, size: 18, color: Colors.blue.shade300),
                          const SizedBox(width: 8),
                          const Text(
                            'Privacy Notice',
                            style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
                          ),
                        ],
                      ),
                      const SizedBox(height: 10),
                      const Text(
                        _explanationText,
                        style: TextStyle(fontSize: 12, height: 1.5, color: Colors.white70),
                      ),
                    ],
                  ),
                ),
              ],
            ),
    );
  }
}

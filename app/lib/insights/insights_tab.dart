import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../core/api_client.dart';
import 'ai_settings_screen.dart';
import 'insight_card.dart';
import 'insights_repository.dart';
import 'models.dart';
import 'mood_card.dart';

class InsightsTab extends StatefulWidget {
  const InsightsTab({super.key});

  @override
  State<InsightsTab> createState() => _InsightsTabState();
}

class _InsightsTabState extends State<InsightsTab> {
  final GlobalKey<InsightCardState> _spendingCardKey = GlobalKey<InsightCardState>();
  final GlobalKey<InsightCardState> _fitnessCardKey = GlobalKey<InsightCardState>();
  final GlobalKey<InsightCardState> _overviewCardKey = GlobalKey<InsightCardState>();

  MoodNudge? _nudge;
  bool _isLoadingNudge = true;

  @override
  void initState() {
    super.initState();
    _loadNudge();
  }

  Future<void> _loadNudge() async {
    try {
      final repo = InsightsRepository(context.read<ApiClient>());
      final nudge = await repo.getNudge();
      if (mounted) {
        setState(() {
          _nudge = nudge.show ? nudge : null;
          _isLoadingNudge = false;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _isLoadingNudge = false;
        });
      }
    }
  }

  Future<void> _dismissNudge() async {
    try {
      final repo = InsightsRepository(context.read<ApiClient>());
      await repo.dismissNudge();
      if (mounted) {
        setState(() {
          _nudge = null;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _nudge = null;
        });
      }
    }
  }

  Future<void> _refreshAll() async {
    await _loadNudge();
    await Future.wait([
      if (_spendingCardKey.currentState != null) _spendingCardKey.currentState!.loadData(),
      if (_fitnessCardKey.currentState != null) _fitnessCardKey.currentState!.loadData(),
      if (_overviewCardKey.currentState != null) _overviewCardKey.currentState!.loadData(),
    ]);
  }

  @override
  Widget build(BuildContext context) {
    final repo = InsightsRepository(context.read<ApiClient>());

    return RefreshIndicator(
      onRefresh: _refreshAll,
      child: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        children: [
          // 1. Nudge Banner (only when show == true)
          if (_nudge != null && _nudge!.show && _nudge!.message != null) ...[
            Container(
              margin: const EdgeInsets.only(bottom: 16),
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: Colors.amber.shade900.withOpacity(0.25),
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: Colors.amber.withOpacity(0.4)),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Icon(Icons.favorite_border, color: Colors.amberAccent, size: 22),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          _nudge!.message!,
                          style: const TextStyle(
                            fontSize: 13,
                            height: 1.4,
                            color: Colors.white,
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Align(
                    alignment: Alignment.centerRight,
                    child: TextButton(
                      style: TextButton.styleFrom(
                        visualDensity: VisualDensity.compact,
                        foregroundColor: Colors.amberAccent,
                      ),
                      onPressed: _dismissNudge,
                      child: const Text('Dismiss'),
                    ),
                  ),
                ],
              ),
            ),
          ],

          // 2. Mood Card
          MoodCard(
            repository: repo,
            onMoodSaved: () {
              // Reload overview card so mood is reflected if mood sharing is enabled
              _overviewCardKey.currentState?.loadData();
              _loadNudge();
            },
          ),
          const SizedBox(height: 16),

          // 3. Three Insight Cards in order:
          // "Spending this month"
          InsightCard(
            key: _spendingCardKey,
            kind: 'spending_month',
            title: 'Spending this month',
            icon: Icons.account_balance_wallet_outlined,
            repository: repo,
          ),
          const SizedBox(height: 16),

          // "Fitness, last 7 days"
          InsightCard(
            key: _fitnessCardKey,
            kind: 'fitness_week',
            title: 'Fitness, last 7 days',
            icon: Icons.fitness_center_outlined,
            repository: repo,
          ),
          const SizedBox(height: 16),

          // "Overview, last 7 days"
          InsightCard(
            key: _overviewCardKey,
            kind: 'overview_week',
            title: 'Overview, last 7 days',
            icon: Icons.auto_graph_outlined,
            repository: repo,
          ),
          const SizedBox(height: 24),

          // 4. AI Settings Link
          Center(
            child: OutlinedButton.icon(
              icon: const Icon(Icons.settings_outlined, size: 18),
              label: const Text('AI settings'),
              style: OutlinedButton.styleFrom(
                padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              ),
              onPressed: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) => AiSettingsScreen(repository: repo),
                  ),
                ).then((_) {
                  // Reload cards in case settings changed
                  _refreshAll();
                });
              },
            ),
          ),
          const SizedBox(height: 32),
        ],
      ),
    );
  }
}

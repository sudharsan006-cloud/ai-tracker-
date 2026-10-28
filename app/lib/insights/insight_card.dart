import 'package:flutter/material.dart';
import 'insights_repository.dart';
import 'models.dart';

class InsightCard extends StatefulWidget {
  final String kind;
  final String title;
  final IconData icon;
  final InsightsRepository repository;

  const InsightCard({
    super.key,
    required this.kind,
    required this.title,
    required this.icon,
    required this.repository,
  });

  @override
  State<InsightCard> createState() => InsightCardState();
}

class InsightCardState extends State<InsightCard> {
  bool _isLoading = true;
  bool _isGenerating = false;
  String? _errorMessage;
  String? _fallbackMessage;
  InsightResponse? _data;

  static const Map<String, String> _fallbackMessages = {
    'AI_DISABLED': 'AI summaries are switched off in AI settings.',
    'AI_NOT_CONFIGURED': 'AI is not set up on the server.',
    'NO_DATA': 'Nothing to summarize yet.',
    'BUDGET_REACHED': 'Daily summary limit reached. Try again tomorrow.',
    'RATE_LIMITED': 'The AI service is busy. Try again in a minute.',
    'MODEL_UNAVAILABLE': 'The AI model is not available. Your numbers above are still correct.',
    'AI_ERROR': 'The summary could not be created. Your numbers above are still correct.',
    'REJECTED_BY_GUARD': 'The summary was not safe to show, so it was discarded. Your numbers above are still correct.',
  };

  @override
  void initState() {
    super.initState();
    loadData();
  }

  Future<void> loadData() async {
    if (!mounted) return;
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final res = await widget.repository.getInsight(widget.kind);
      if (mounted) {
        setState(() {
          _data = res;
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

  Future<void> _generateSummary() async {
    setState(() {
      _isGenerating = true;
      _fallbackMessage = null;
    });

    try {
      final gen = await widget.repository.generateInsight(widget.kind);
      if (mounted) {
        setState(() {
          if (gen.insight != null) {
            _data = InsightResponse(
              kind: _data?.kind ?? widget.kind,
              periodStart: _data?.periodStart ?? '',
              periodEnd: _data?.periodEnd ?? '',
              facts: _data?.facts ?? {},
              hasData: _data?.hasData ?? true,
              aiEnabled: _data?.aiEnabled ?? true,
              aiConfigured: _data?.aiConfigured ?? true,
              callsRemainingToday: gen.callsRemainingToday,
              insight: gen.insight,
            );
            _fallbackMessage = null;
          } else if (gen.fallbackReason != null) {
            _fallbackMessage = _fallbackMessages[gen.fallbackReason] ?? gen.fallbackReason;
            _data = InsightResponse(
              kind: _data?.kind ?? widget.kind,
              periodStart: _data?.periodStart ?? '',
              periodEnd: _data?.periodEnd ?? '',
              facts: _data?.facts ?? {},
              hasData: _data?.hasData ?? true,
              aiEnabled: _data?.aiEnabled ?? true,
              aiConfigured: _data?.aiConfigured ?? true,
              callsRemainingToday: gen.callsRemainingToday,
              insight: _data?.insight,
            );
          }
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _fallbackMessage = e.toString().replaceFirst('Exception: ', '');
        });
      }
    } finally {
      if (mounted) {
        setState(() {
          _isGenerating = false;
        });
      }
    }
  }

  Widget _buildRow(String label, String value, {Color? valueColor, bool isSub = false}) {
    return Padding(
      padding: EdgeInsets.symmetric(vertical: isSub ? 2.0 : 4.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Expanded(
            child: Text(
              label,
              style: TextStyle(
                color: isSub ? Colors.white60 : Colors.white70,
                fontSize: isSub ? 12 : 13,
              ),
            ),
          ),
          Text(
            value,
            style: TextStyle(
              fontWeight: isSub ? FontWeight.normal : FontWeight.w600,
              color: valueColor ?? Colors.white,
              fontSize: isSub ? 12 : 13,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSpendingFacts(Map<String, dynamic> f) {
    final sym = f['currencySymbol']?.toString() ?? '₹';
    final totalSpent = f['totalSpent']?.toString() ?? '0.00';
    final totalIncome = f['totalIncome']?.toString() ?? '0.00';
    final prevSpent = f['previousPeriodSpent']?.toString() ?? '0.00';
    final changeAmt = f['changeAmount']?.toString() ?? '0.00';
    final changePct = f['changePercent']?.toString();
    final topCats = (f['topCategories'] as List<dynamic>?) ?? [];
    final budgets = (f['budgets'] as List<dynamic>?) ?? [];

    String changeStr = '$sym$changeAmt';
    if (changePct != null) {
      changeStr += ' ($changePct%)';
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _buildRow('Spent', '$sym$totalSpent'),
        _buildRow('Income', '$sym$totalIncome'),
        _buildRow('Same period last month', '$sym$prevSpent'),
        _buildRow('Change', changeStr),

        if (topCats.isNotEmpty) ...[
          const SizedBox(height: 8),
          const Text('Top categories', style: TextStyle(color: Colors.white54, fontSize: 11, fontWeight: FontWeight.bold)),
          const SizedBox(height: 4),
          ...topCats.map((cat) {
            final name = cat['name']?.toString() ?? 'Uncategorized';
            final amt = cat['spent']?.toString() ?? '0.00';
            final share = cat['sharePercent']?.toString() ?? '0.0';
            return _buildRow(name, '$sym$amt ($share%)', isSub: true);
          }),
        ],

        if (budgets.isNotEmpty) ...[
          const SizedBox(height: 8),
          const Text('Budgets', style: TextStyle(color: Colors.white54, fontSize: 11, fontWeight: FontWeight.bold)),
          const SizedBox(height: 4),
          ...budgets.map((b) {
            final label = b['label']?.toString() ?? 'Budget';
            final spent = b['spent']?.toString() ?? '0.00';
            final amt = b['amount']?.toString() ?? '0.00';
            final pct = b['percentUsed']?.toString() ?? '0.0';
            final isOver = b['overBudget'] == true;
            return _buildRow(
              label,
              '$sym$spent of $sym$amt ($pct%)',
              valueColor: isOver ? Colors.redAccent : null,
              isSub: true,
            );
          }),
        ],
      ],
    );
  }

  Widget _buildFitnessFacts(Map<String, dynamic> f) {
    final daysWithData = f['daysWithData']?.toString() ?? '0';
    final avgSteps = f['averageSteps']?.toString() ?? '0';
    final avgWater = f['averageWaterMl']?.toString() ?? '0';
    final avgCaloriesIn = f['averageCaloriesIn']?.toString() ?? '0';
    final daysStepsGoalMet = f['daysStepsGoalMet']?.toString() ?? '0';
    final daysWaterGoalMet = f['daysWaterGoalMet']?.toString() ?? '0';
    final periodDays = f['periodDays']?.toString() ?? '7';
    final workoutCount = f['workoutCount']?.toString() ?? '0';
    final workoutMin = f['workoutTotalMinutes']?.toString() ?? '0';
    final calBurned = f['caloriesBurnedEstTotal']?.toString() ?? '0';
    final bestSteps = f['bestStepsDay'] as Map<String, dynamic>?;

    String bestStepsStr = '-';
    if (bestSteps != null && bestSteps['date'] != null) {
      bestStepsStr = "${bestSteps['steps']} steps on ${bestSteps['date']}";
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _buildRow('Days with data', daysWithData),
        _buildRow('Average steps', avgSteps),
        _buildRow('Average water (ml)', avgWater),
        _buildRow('Average calories in', avgCaloriesIn),
        _buildRow('Days steps goal met', '$daysStepsGoalMet of $periodDays'),
        _buildRow('Days water goal met', '$daysWaterGoalMet of $periodDays'),
        _buildRow('Workouts', '$workoutCount ($workoutMin min)'),
        _buildRow('Calories burned (estimate)', calBurned),
        _buildRow('Best steps day', bestStepsStr),
      ],
    );
  }

  Widget _buildOverviewFacts(Map<String, dynamic> f) {
    final sym = f['currencySymbol']?.toString() ?? '₹';
    final spent7 = f['spentLast7Days']?.toString() ?? '0.00';
    final topCat = f['topSpendingCategory'] as Map<String, dynamic>?;
    final avgSteps = f['averageSteps']?.toString() ?? '0';
    final avgWater = f['averageWaterMl']?.toString() ?? '0';
    final workoutCount = f['workoutCount']?.toString() ?? '0';
    final mood = f['mood'] as Map<String, dynamic>?;

    String topCatStr = '-';
    if (topCat != null && topCat['name'] != null) {
      topCatStr = "${topCat['name']} ($sym${topCat['spent']})";
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _buildRow('Spent (7 days)', '$sym$spent7'),
        _buildRow('Top category', topCatStr),
        _buildRow('Average steps', avgSteps),
        _buildRow('Average water (ml)', avgWater),
        _buildRow('Workouts', workoutCount),

        if (mood != null) ...[
          const SizedBox(height: 8),
          const Text('Mood (7 days)', style: TextStyle(color: Colors.white54, fontSize: 11, fontWeight: FontWeight.bold)),
          const SizedBox(height: 4),
          _buildRow('Entries', mood['entries']?.toString() ?? '0', isSub: true),
          _buildRow('Average score', mood['averageScore']?.toString() ?? '-', isSub: true),
          _buildRow('Low days', mood['lowDays']?.toString() ?? '0', isSub: true),
        ],
      ],
    );
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
            // Card Title Header
            Row(
              children: [
                Icon(widget.icon, size: 20, color: theme.colorScheme.primary),
                const SizedBox(width: 8),
                Text(
                  widget.title,
                  style: theme.textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
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
            else if (_errorMessage != null)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 12),
                child: Text(_errorMessage!, style: const TextStyle(color: Colors.redAccent, fontSize: 13)),
              )
            else if (_data == null || !_data!.hasData) ...[
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 8),
                child: Text(
                  'Nothing to summarize yet.',
                  style: TextStyle(color: Colors.white60, fontSize: 13),
                ),
              ),
            ] else ...[
              // The Numbers (always shown first)
              if (widget.kind == 'spending_month')
                _buildSpendingFacts(_data!.facts)
              else if (widget.kind == 'fitness_week')
                _buildFitnessFacts(_data!.facts)
              else
                _buildOverviewFacts(_data!.facts),

              // Fallback reason message (if any)
              if (_fallbackMessage != null) ...[
                const SizedBox(height: 10),
                Text(
                  _fallbackMessage!,
                  style: const TextStyle(color: Colors.white60, fontSize: 12, fontStyle: FontStyle.italic),
                ),
              ],

              // AI Summary Section
              if (_data!.insight != null) ...[
                const Divider(height: 24),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: Colors.white.withOpacity(0.04),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: Colors.white.withOpacity(0.06)),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        crossAxisAlignment: CrossAxisAlignment.center,
                        children: [
                          const Icon(Icons.auto_awesome, size: 16, color: Colors.amberAccent),
                          const SizedBox(width: 6),
                          const Text(
                            'AI Summary',
                            style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: Colors.white),
                          ),
                          const Spacer(),
                          if (_data!.insight!.stale)
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                              decoration: BoxDecoration(
                                color: Colors.orange.withOpacity(0.2),
                                borderRadius: BorderRadius.circular(12),
                                border: Border.all(color: Colors.orangeAccent),
                              ),
                              child: const Text(
                                'Out of date: your data changed',
                                style: TextStyle(color: Colors.orangeAccent, fontSize: 10, fontWeight: FontWeight.w500),
                              ),
                            ),
                        ],
                      ),
                      const SizedBox(height: 8),
                      Text(
                        _data!.insight!.text,
                        style: const TextStyle(fontSize: 13, height: 1.4, color: Colors.white),
                      ),
                      const SizedBox(height: 8),
                      const Text(
                        'AI-written from the numbers above. Not financial or medical advice.',
                        style: TextStyle(color: Colors.white38, fontSize: 11),
                      ),
                    ],
                  ),
                ),
              ],

              const SizedBox(height: 14),

              // Button & hints
              _buildButtonSection(theme),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildButtonSection(ThemeData theme) {
    if (!_data!.aiEnabled) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: 4),
        child: Text(
          'AI summaries are switched off in AI settings',
          style: TextStyle(color: Colors.white54, fontSize: 12, fontStyle: FontStyle.italic),
        ),
      );
    }

    if (!_data!.aiConfigured) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: 4),
        child: Text(
          'AI is not set up on the server',
          style: TextStyle(color: Colors.white54, fontSize: 12, fontStyle: FontStyle.italic),
        ),
      );
    }

    final hasInsight = _data!.insight != null;
    final buttonLabel = hasInsight ? 'Refresh summary' : 'Generate summary';

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        ElevatedButton.icon(
          onPressed: _isGenerating ? null : _generateSummary,
          icon: _isGenerating
              ? const SizedBox(
                  width: 16,
                  height: 16,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              : const Icon(Icons.auto_awesome, size: 16),
          label: Text(buttonLabel),
          style: ElevatedButton.styleFrom(
            padding: const EdgeInsets.symmetric(vertical: 10),
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
          ),
        ),
        const SizedBox(height: 6),
        Center(
          child: Text(
            '${_data!.callsRemainingToday} summaries left today',
            style: const TextStyle(color: Colors.white38, fontSize: 11),
          ),
        ),
      ],
    );
  }
}

import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import '../core/api_client.dart';
import 'fitness_repository.dart';
import 'models.dart';

class ChartsScreen extends StatefulWidget {
  const ChartsScreen({super.key});

  @override
  State<ChartsScreen> createState() => _ChartsScreenState();
}

class _ChartsScreenState extends State<ChartsScreen> {
  int _daysCount = 7;
  bool _isLoading = true;
  String? _errorMessage;
  RangeResponse? _rangeData;

  @override
  void initState() {
    super.initState();
    _loadRange();
  }

  Future<void> _loadRange() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    final now = DateTime.now();
    final toDate = DateFormat('yyyy-MM-dd').format(now);
    final fromDate = DateFormat('yyyy-MM-dd').format(now.subtract(Duration(days: _daysCount - 1)));

    final repo = FitnessRepository(context.read<ApiClient>());
    try {
      final res = await repo.getRange(from: fromDate, to: toDate);
      if (mounted) {
        setState(() {
          _rangeData = res;
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

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Fitness Charts'),
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(12.0),
            child: SegmentedButton<int>(
              segments: const [
                ButtonSegment<int>(value: 7, label: Text('7 days')),
                ButtonSegment<int>(value: 30, label: Text('30 days')),
              ],
              selected: {_daysCount},
              onSelectionChanged: (newSelection) {
                setState(() {
                  _daysCount = newSelection.first;
                });
                _loadRange();
              },
            ),
          ),
          Expanded(
            child: _isLoading
                ? const Center(child: CircularProgressIndicator())
                : _errorMessage != null
                    ? Center(
                        child: Text(
                          _errorMessage!,
                          style: const TextStyle(color: Colors.redAccent),
                        ),
                      )
                    : _rangeData == null || _rangeData!.days.isEmpty
                        ? const Center(child: Text('No data yet'))
                        : _buildChartsList(),
          ),
        ],
      ),
    );
  }

  Widget _buildChartsList() {
    final days = _rangeData!.days;
    final avg = _rangeData!.averages;

    return ListView(
      padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
      children: [
        _buildSingleBarChart(
          title: 'Steps',
          days: days,
          valueSelector: (d) => d.steps.toDouble(),
          barColor: Colors.blueAccent,
          averageText: 'Average: ${avg.steps}',
        ),
        const SizedBox(height: 24),
        _buildSingleBarChart(
          title: 'Water (ml)',
          days: days,
          valueSelector: (d) => d.waterMl.toDouble(),
          barColor: Colors.cyanAccent,
          averageText: 'Average: ${avg.waterMl} ml',
        ),
        const SizedBox(height: 24),
        _buildSingleBarChart(
          title: 'Calories in (kcal)',
          days: days,
          valueSelector: (d) => d.caloriesIn.toDouble(),
          barColor: Colors.orangeAccent,
          averageText: 'Average: ${avg.caloriesIn} kcal',
        ),
        const SizedBox(height: 32),
      ],
    );
  }

  Widget _buildSingleBarChart({
    required String title,
    required List<RangeDay> days,
    required double Function(RangeDay) valueSelector,
    required Color barColor,
    required String averageText,
  }) {
    double maxY = 0;
    final barGroups = <BarChartGroupData>[];

    for (int i = 0; i < days.length; i++) {
      final val = valueSelector(days[i]);
      if (val > maxY) maxY = val;
      barGroups.add(
        BarChartGroupData(
          x: i,
          barRods: [
            BarChartRodData(
              toY: val,
              color: barColor,
              width: days.length > 15 ? 6 : 14,
              borderRadius: const BorderRadius.vertical(top: Radius.circular(4)),
            ),
          ],
        ),
      );
    }

    if (maxY == 0) {
      maxY = 10;
    } else {
      maxY = maxY * 1.15;
    }

    return Card(
      elevation: 2,
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              title,
              style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 16),
            SizedBox(
              height: 180,
              child: BarChart(
                BarChartData(
                  maxY: maxY,
                  minY: 0,
                  gridData: const FlGridData(show: false),
                  borderData: FlBorderData(show: false),
                  titlesData: FlTitlesData(
                    topTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
                    rightTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
                    leftTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
                    bottomTitles: AxisTitles(
                      sideTitles: SideTitles(
                        showTitles: true,
                        reservedSize: 26,
                        getTitlesWidget: (value, meta) {
                          final index = value.toInt();
                          if (index < 0 || index >= days.length) {
                            return const SizedBox.shrink();
                          }
                          // In 30 days view, show every 5th label
                          if (days.length > 15 && index % 5 != 0 && index != days.length - 1) {
                            return const SizedBox.shrink();
                          }
                          final dateStr = days[index].date;
                          final dayNum = DateTime.tryParse(dateStr)?.day.toString() ?? dateStr;
                          return SideTitleWidget(
                            axisSide: meta.axisSide,
                            child: Text(
                              dayNum,
                              style: const TextStyle(fontSize: 10, color: Colors.white70),
                            ),
                          );
                        },
                      ),
                    ),
                  ),
                  barGroups: barGroups,
                ),
              ),
            ),
            const SizedBox(height: 12),
            Text(
              averageText,
              style: const TextStyle(fontSize: 14, color: Colors.white70),
            ),
          ],
        ),
      ),
    );
  }
}

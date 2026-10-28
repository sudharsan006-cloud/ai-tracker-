import 'package:flutter/material.dart';
import '../bank_accounts/bank_accounts_screen.dart';
import '../expenses/expenses_tab.dart';
import '../fitness/fitness_tab.dart';
import '../insights/insights_tab.dart';
import '../profile/profile_screen.dart';
import '../reminders/notification_service.dart';
import '../reminders/reminder_settings.dart';
import '../statements/statement_import_screen.dart';

class HomeShell extends StatefulWidget {
  const HomeShell({super.key});

  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> {
  int _currentIndex = 0;

  static const List<String> _tabTitles = [
    'Expenses',
    'Fitness',
    'Insights',
  ];

  @override
  void initState() {
    super.initState();
    _rescheduleReminders();
  }

  Future<void> _rescheduleReminders() async {
    try {
      final settings = await ReminderSettings.load();
      await NotificationService.rescheduleAll(settings);
    } catch (_) {
      // Ignored if notifications not available
    }
  }

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final isWide = constraints.maxWidth >= 900;

        final currentBody = _currentIndex == 0
            ? const ExpensesTab()
            : _currentIndex == 1
                ? const FitnessTab()
                : const InsightsTab();

        final content = Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 800),
            child: currentBody,
          ),
        );

        return Scaffold(
          appBar: AppBar(
            title: Text(_tabTitles[_currentIndex]),
            actions: [
              if (_currentIndex == 0)
                IconButton(
                  icon: const Icon(Icons.upload_file),
                  tooltip: 'Import statement',
                  onPressed: () {
                    Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (_) => const StatementImportScreen(),
                      ),
                    );
                  },
                ),
              IconButton(
                icon: const Icon(Icons.account_balance),
                tooltip: 'Bank Accounts',
                onPressed: () {
                  Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) => const BankAccountsScreen(),
                    ),
                  );
                },
              ),
              IconButton(
                icon: const Icon(Icons.person_outline),
                tooltip: 'Profile',
                onPressed: () {
                  Navigator.push(
                    context,
                    MaterialPageRoute(builder: (_) => const ProfileScreen()),
                  );
                },
              ),
            ],
          ),
          body: isWide
              ? Row(
                  children: [
                    NavigationRail(
                      selectedIndex: _currentIndex,
                      onDestinationSelected: (index) {
                        setState(() {
                          _currentIndex = index;
                        });
                      },
                      labelType: NavigationRailLabelType.all,
                      destinations: const [
                        NavigationRailDestination(
                          icon: Icon(Icons.receipt_long_outlined),
                          selectedIcon: Icon(Icons.receipt_long),
                          label: Text('Expenses'),
                        ),
                        NavigationRailDestination(
                          icon: Icon(Icons.directions_run_outlined),
                          selectedIcon: Icon(Icons.directions_run),
                          label: Text('Fitness'),
                        ),
                        NavigationRailDestination(
                          icon: Icon(Icons.insights_outlined),
                          selectedIcon: Icon(Icons.insights),
                          label: Text('Insights'),
                        ),
                      ],
                    ),
                    const VerticalDivider(thickness: 1, width: 1),
                    Expanded(child: content),
                  ],
                )
              : currentBody,
          bottomNavigationBar: isWide
              ? null
              : NavigationBar(
                  selectedIndex: _currentIndex,
                  onDestinationSelected: (index) {
                    setState(() {
                      _currentIndex = index;
                    });
                  },
                  destinations: const [
                    NavigationDestination(
                      icon: Icon(Icons.receipt_long_outlined),
                      selectedIcon: Icon(Icons.receipt_long),
                      label: 'Expenses',
                    ),
                    NavigationDestination(
                      icon: Icon(Icons.directions_run_outlined),
                      selectedIcon: Icon(Icons.directions_run),
                      label: 'Fitness',
                    ),
                    NavigationDestination(
                      icon: Icon(Icons.insights_outlined),
                      selectedIcon: Icon(Icons.insights),
                      label: 'Insights',
                    ),
                  ],
                ),
        );
      },
    );
  }
}

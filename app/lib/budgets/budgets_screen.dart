import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../auth/auth_service.dart';
import '../core/widgets/empty_state_widget.dart';
import '../core/widgets/loading_state_widget.dart';
import '../expenses/expense_repository.dart';
import '../expenses/models.dart';

class BudgetsScreen extends StatefulWidget {
  const BudgetsScreen({super.key});

  @override
  State<BudgetsScreen> createState() => _BudgetsScreenState();
}

class _BudgetsScreenState extends State<BudgetsScreen> {
  late ExpenseRepository _repository;
  bool _isLoading = true;
  String? _errorMessage;

  List<Budget> _budgets = [];
  List<Category> _categories = [];
  List<Map<String, dynamic>> _currencies = [];

  @override
  void initState() {
    super.initState();
    _repository = ExpenseRepository(context.read<AuthService>().apiClient);
    _loadAll();
  }

  Future<void> _loadAll() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final futures = await Future.wait([
        _repository.getBudgets(),
        _repository.getCategories(),
        _repository.getCurrencies(),
      ]);

      if (mounted) {
        setState(() {
          _budgets = futures[0] as List<Budget>;
          _categories = futures[1] as List<Category>;
          _currencies = futures[2] as List<Map<String, dynamic>>;
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

  String _getCategoryName(String? categoryId) {
    if (categoryId == null) return 'Overall';
    final match = _categories.where((c) => c.id == categoryId).toList();
    return match.isNotEmpty ? match.first.name : 'Unknown Category';
  }

  String _getCurrencySymbol(int currencyId) {
    final match = _currencies.where((c) => c['id'] == currencyId).toList();
    if (match.isNotEmpty) {
      return match.first['symbol'] as String? ?? match.first['code'] as String? ?? '';
    }
    return '';
  }

  void _showAddBudgetDialog() {
    String? selectedCategoryId; // null = Overall
    int selectedCurrencyId = _currencies.isNotEmpty ? _currencies.first['id'] as int : 1;
    final amountController = TextEditingController();
    String? dialogError;

    final expenseCategories = _categories.where((c) => c.type == 'expense').toList();

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDialogState) {
          return AlertDialog(
            title: const Text('Add Monthly Budget'),
            content: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  DropdownButtonFormField<String?>(
                    value: selectedCategoryId,
                    decoration: const InputDecoration(labelText: 'Scope'),
                    items: [
                      const DropdownMenuItem<String?>(
                        value: null,
                        child: Text('Overall (All Expenses)'),
                      ),
                      ...expenseCategories.map((c) => DropdownMenuItem<String?>(
                            value: c.id,
                            child: Text(c.name),
                          )),
                    ],
                    onChanged: (val) {
                      setDialogState(() => selectedCategoryId = val);
                    },
                  ),
                  const SizedBox(height: 12),
                  DropdownButtonFormField<int>(
                    value: selectedCurrencyId,
                    decoration: const InputDecoration(labelText: 'Currency'),
                    items: _currencies.map((c) {
                      final id = c['id'] as int;
                      final code = c['code'] as String;
                      final symbol = c['symbol'] as String? ?? '';
                      return DropdownMenuItem<int>(
                        value: id,
                        child: Text('$code ($symbol)'),
                      );
                    }).toList(),
                    onChanged: (val) {
                      if (val != null) {
                        setDialogState(() => selectedCurrencyId = val);
                      }
                    },
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: amountController,
                    decoration: const InputDecoration(
                      labelText: 'Monthly Amount',
                      hintText: 'e.g. 5000.00',
                    ),
                    keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  ),
                  if (dialogError != null) ...[
                    const SizedBox(height: 12),
                    Text(
                      dialogError!,
                      style: const TextStyle(color: Colors.redAccent, fontSize: 13),
                    ),
                  ],
                ],
              ),
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(ctx),
                child: const Text('Cancel'),
              ),
              FilledButton(
                onPressed: () async {
                  final amountText = amountController.text.trim();
                  final amount = num.tryParse(amountText);
                  if (amount == null || amount <= 0) {
                    setDialogState(() => dialogError = 'Enter a valid amount greater than 0');
                    return;
                  }
                  try {
                    await _repository.createBudget(
                      categoryId: selectedCategoryId,
                      currencyId: selectedCurrencyId,
                      amount: amount,
                    );
                    if (mounted) {
                      Navigator.pop(ctx);
                      _loadAll();
                    }
                  } catch (e) {
                    setDialogState(() {
                      dialogError = e.toString().replaceFirst('Exception: ', '');
                    });
                  }
                },
                child: const Text('Add'),
              ),
            ],
          );
        },
      ),
    );
  }

  void _showEditBudgetDialog(Budget budget) {
    final amountController = TextEditingController(text: budget.amountFormatted);
    String? dialogError;

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDialogState) {
          return AlertDialog(
            title: Text('Edit Budget (${_getCategoryName(budget.categoryId)})'),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: amountController,
                  decoration: const InputDecoration(labelText: 'Amount'),
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                ),
                if (dialogError != null) ...[
                  const SizedBox(height: 12),
                  Text(
                    dialogError!,
                    style: const TextStyle(color: Colors.redAccent, fontSize: 13),
                  ),
                ],
              ],
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(ctx),
                child: const Text('Cancel'),
              ),
              FilledButton(
                onPressed: () async {
                  final amountText = amountController.text.trim();
                  final amount = num.tryParse(amountText);
                  if (amount == null || amount <= 0) {
                    setDialogState(() => dialogError = 'Enter a valid amount greater than 0');
                    return;
                  }
                  try {
                    await _repository.patchBudget(budget.id, amount);
                    if (mounted) {
                      Navigator.pop(ctx);
                      _loadAll();
                    }
                  } catch (e) {
                    setDialogState(() {
                      dialogError = e.toString().replaceFirst('Exception: ', '');
                    });
                  }
                },
                child: const Text('Save'),
              ),
            ],
          );
        },
      ),
    );
  }

  void _confirmDeleteBudget(Budget budget) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Delete Budget'),
        content: Text('Are you sure you want to delete the budget for ${_getCategoryName(budget.categoryId)}?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: Colors.red),
            onPressed: () async {
              try {
                await _repository.deleteBudget(budget.id);
                if (mounted) {
                  Navigator.pop(ctx);
                  _loadAll();
                }
              } catch (e) {
                if (mounted) {
                  Navigator.pop(ctx);
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(content: Text(e.toString().replaceFirst('Exception: ', ''))),
                  );
                }
              }
            },
            child: const Text('Delete'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Budgets'),
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: _showAddBudgetDialog,
        tooltip: 'Add Budget',
        child: const Icon(Icons.add),
      ),
      body: _buildBody(),
    );
  }

  Widget _buildBody() {
    if (_isLoading) {
      return const LoadingStateWidget();
    }

    if (_errorMessage != null) {
      return ErrorStateWidget(
        message: _errorMessage,
        onRetry: _loadAll,
      );
    }

    if (_budgets.isEmpty) {
      return EmptyStateWidget(
        icon: Icons.savings_outlined,
        message: 'No budgets configured for this month.',
        actionLabel: 'Add Budget',
        onAction: _showAddBudgetDialog,
      );
    }

    return RefreshIndicator(
      onRefresh: _loadAll,
      child: ListView.builder(
        padding: const EdgeInsets.all(12),
        itemCount: _budgets.length,
        itemBuilder: (context, index) {
        final b = _budgets[index];
        final currencySymbol = _getCurrencySymbol(b.currencyId);
        final progress = (b.percentUsed / 100.0).clamp(0.0, 1.0);
        final isOver = b.overBudget;

        return Card(
          margin: const EdgeInsets.only(bottom: 12),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Expanded(
                      child: Text(
                        _getCategoryName(b.categoryId),
                        style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                      ),
                    ),
                    IconButton(
                      icon: const Icon(Icons.edit, size: 20),
                      onPressed: () => _showEditBudgetDialog(b),
                      tooltip: 'Edit',
                    ),
                    IconButton(
                      icon: const Icon(Icons.delete_outline, size: 20, color: Colors.redAccent),
                      onPressed: () => _confirmDeleteBudget(b),
                      tooltip: 'Delete',
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                ClipRRect(
                  borderRadius: BorderRadius.circular(4),
                  child: LinearProgressIndicator(
                    value: progress,
                    minHeight: 8,
                    backgroundColor: Colors.white12,
                    valueColor: AlwaysStoppedAnimation<Color>(
                      isOver ? Colors.redAccent : Colors.tealAccent,
                    ),
                  ),
                ),
                const SizedBox(height: 12),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      'Spent: $currencySymbol${b.spentFormatted} / $currencySymbol${b.amountFormatted}',
                      style: const TextStyle(fontSize: 13, color: Colors.white70),
                    ),
                    Text(
                      '${b.percentUsedFormatted}%',
                      style: TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.bold,
                        color: isOver ? Colors.redAccent : Colors.tealAccent,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 4),
                Text(
                  isOver
                      ? 'Over budget by $currencySymbol${(b.spent - b.amount).toStringAsFixed(2)}'
                      : 'Remaining: $currencySymbol${b.remainingFormatted}',
                  style: TextStyle(
                    fontSize: 13,
                    color: isOver ? Colors.redAccent : Colors.white60,
                  ),
                ),
              ],
            ),
          ),
        );
      },
    ),
  );
}
}

import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import '../auth/auth_service.dart';
import '../budgets/budgets_screen.dart';
import '../categories/categories_screen.dart';
import '../core/platform_capabilities.dart';
import '../core/widgets/empty_state_widget.dart';
import '../core/widgets/loading_state_widget.dart';
import '../sms/sms_sync_service.dart';
import 'expense_repository.dart';
import 'models.dart';
import 'review_queue_screen.dart';
import 'transaction_form_screen.dart';
import '../statements/statement_import_screen.dart';

class ExpensesTab extends StatefulWidget {
  const ExpensesTab({super.key});

  @override
  State<ExpensesTab> createState() => _ExpensesTabState();
}

class _ExpensesTabState extends State<ExpensesTab> {
  late ExpenseRepository _repository;
  late SmsSyncService _smsSyncService;

  final ScrollController _scrollController = ScrollController();

  bool _isLoading = true;
  bool _isLoadingMore = false;
  String? _errorMessage;

  List<Transaction> _transactions = [];
  int _currentPage = 0;
  final int _pageSize = 50;
  int _totalTransactions = 0;
  bool _hasMore = true;

  int _reviewQueueCount = 0;
  bool _hasAutoSyncedSms = false;

  // Filter state
  DateTimeRange? _selectedDateRange;
  String? _selectedCategoryId;
  String? _selectedBankAccountId;
  String _selectedDirection = 'all'; // 'all', 'debit', 'credit'

  List<Category> _categories = [];
  List<Map<String, dynamic>> _bankAccounts = [];
  List<Map<String, dynamic>> _currencies = [];

  @override
  void initState() {
    super.initState();
    final apiClient = context.read<AuthService>().apiClient;
    _repository = ExpenseRepository(apiClient);
    _smsSyncService = SmsSyncService(_repository);

    _scrollController.addListener(_onScroll);

    _initialize();
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  void _onScroll() {
    if (_scrollController.position.pixels >= _scrollController.position.maxScrollExtent - 200) {
      if (!_isLoading && !_isLoadingMore && _hasMore) {
        _loadMoreTransactions();
      }
    }
  }

  Future<void> _initialize() async {
    await _loadFiltersData();
    await _loadTransactions(reset: true);
    await _loadReviewQueueCount();

    // Auto-sync SMS on first open (Android only)
    if (!_hasAutoSyncedSms && PlatformCapabilities.supportsSms) {
      _hasAutoSyncedSms = true;
      _triggerSmsSync(silent: true);
    }
  }

  Future<void> _loadFiltersData() async {
    try {
      final futures = await Future.wait([
        _repository.getCategories(),
        _repository.getBankAccounts(),
        _repository.getCurrencies(),
      ]);

      if (mounted) {
        setState(() {
          _categories = futures[0] as List<Category>;
          _bankAccounts = futures[1] as List<Map<String, dynamic>>;
          _currencies = futures[2] as List<Map<String, dynamic>>;
        });
      }
    } catch (_) {}
  }

  Future<void> _loadReviewQueueCount() async {
    try {
      final queue = await _repository.getReviewQueue();
      if (mounted) {
        setState(() {
          _reviewQueueCount = queue.totalCount;
        });
      }
    } catch (_) {}
  }

  Future<void> _loadTransactions({bool reset = false}) async {
    if (reset) {
      setState(() {
        _isLoading = true;
        _errorMessage = null;
        _currentPage = 0;
        _transactions = [];
        _hasMore = true;
      });
    }

    try {
      final dateFormat = DateFormat('yyyy-MM-dd');
      final fromStr = _selectedDateRange != null ? dateFormat.format(_selectedDateRange!.start) : null;
      final toStr = _selectedDateRange != null ? dateFormat.format(_selectedDateRange!.end) : null;
      final dirStr = _selectedDirection == 'all' ? null : _selectedDirection;

      final page = await _repository.getTransactions(
        from: fromStr,
        to: toStr,
        categoryId: _selectedCategoryId,
        bankAccountId: _selectedBankAccountId,
        direction: dirStr,
        page: _currentPage,
        size: _pageSize,
      );

      if (mounted) {
        setState(() {
          _transactions = reset ? page.items : [..._transactions, ...page.items];
          _totalTransactions = page.total;
          _hasMore = _transactions.length < _totalTransactions;
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

  Future<void> _loadMoreTransactions() async {
    setState(() => _isLoadingMore = true);
    _currentPage++;

    try {
      final dateFormat = DateFormat('yyyy-MM-dd');
      final fromStr = _selectedDateRange != null ? dateFormat.format(_selectedDateRange!.start) : null;
      final toStr = _selectedDateRange != null ? dateFormat.format(_selectedDateRange!.end) : null;
      final dirStr = _selectedDirection == 'all' ? null : _selectedDirection;

      final page = await _repository.getTransactions(
        from: fromStr,
        to: toStr,
        categoryId: _selectedCategoryId,
        bankAccountId: _selectedBankAccountId,
        direction: dirStr,
        page: _currentPage,
        size: _pageSize,
      );

      if (mounted) {
        setState(() {
          _transactions.addAll(page.items);
          _hasMore = _transactions.length < page.total;
          _isLoadingMore = false;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() => _isLoadingMore = false);
      }
    }
  }

  Future<void> _triggerSmsSync({bool silent = false}) async {
    if (!silent && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Syncing SMS...'), duration: Duration(seconds: 1)),
      );
    }

    try {
      final result = await _smsSyncService.sync();
      if (result == null) {
        if (!silent && mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('SMS permission needed or not supported')),
          );
        }
        return;
      }

      if (mounted) {
        if (!silent || result.created > 0 || result.queued > 0) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Imported ${result.created}, ${result.queued} to review')),
          );
        }
        _loadTransactions(reset: true);
        _loadReviewQueueCount();
      }
    } catch (e) {
      if (!silent && mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Sync failed: ${e.toString().replaceFirst('Exception: ', '')}')),
        );
      }
    }
  }

  String _getCategoryName(String? categoryId) {
    if (categoryId == null) return 'Uncategorized';
    final match = _categories.where((c) => c.id == categoryId).toList();
    return match.isNotEmpty ? match.first.name : 'Uncategorized';
  }

  String _getCurrencySymbol(int currencyId) {
    final match = _currencies.where((c) => c['id'] == currencyId).toList();
    if (match.isNotEmpty) {
      return match.first['symbol'] as String? ?? match.first['code'] as String? ?? '';
    }
    return '';
  }

  Future<void> _pickDateRange() async {
    final picked = await showDateRangePicker(
      context: context,
      firstDate: DateTime(2000),
      lastDate: DateTime.now().add(const Duration(days: 1)),
      initialDateRange: _selectedDateRange,
    );

    if (picked != _selectedDateRange) {
      setState(() => _selectedDateRange = picked);
      _loadTransactions(reset: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      floatingActionButton: FloatingActionButton(
        onPressed: () async {
          final result = await Navigator.push<bool>(
            context,
            MaterialPageRoute(builder: (_) => const TransactionFormScreen()),
          );
          if (result == true) {
            _loadTransactions(reset: true);
            _loadReviewQueueCount();
          }
        },
        tooltip: 'Add Transaction',
        child: const Icon(Icons.add),
      ),
      body: Column(
        children: [
          _buildActionHeader(),
          _buildFilterBar(),
          const Divider(height: 1),
          Expanded(child: _buildTransactionList()),
        ],
      ),
    );
  }

  Widget _buildActionHeader() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      color: Colors.white.withOpacity(0.03),
      child: Row(
        children: [
          // Review Queue button with badge
          Stack(
            clipBehavior: Clip.none,
            children: [
              OutlinedButton.icon(
                icon: const Icon(Icons.rate_review_outlined, size: 18),
                label: const Text('Review Queue'),
                onPressed: () async {
                  await Navigator.push(
                    context,
                    MaterialPageRoute(builder: (_) => const ReviewQueueScreen()),
                  );
                  _loadTransactions(reset: true);
                  _loadReviewQueueCount();
                },
              ),
              if (_reviewQueueCount > 0)
                Positioned(
                  right: -6,
                  top: -6,
                  child: Container(
                    padding: const EdgeInsets.all(5),
                    decoration: const BoxDecoration(
                      color: Colors.amber,
                      shape: BoxShape.circle,
                    ),
                    child: Text(
                      '$_reviewQueueCount',
                      style: const TextStyle(
                        color: Colors.black,
                        fontSize: 11,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                ),
            ],
          ),
          const SizedBox(width: 8),

          IconButton(
            icon: const Icon(Icons.category_outlined),
            tooltip: 'Categories',
            onPressed: () async {
              await Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const CategoriesScreen()),
              );
              _loadFiltersData();
              _loadTransactions(reset: true);
            },
          ),

          IconButton(
            icon: const Icon(Icons.pie_chart_outline),
            tooltip: 'Budgets',
            onPressed: () {
              Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const BudgetsScreen()),
              );
            },
          ),

          IconButton(
            icon: const Icon(Icons.upload_file),
            tooltip: 'Import statement',
            onPressed: () async {
              final result = await Navigator.push<bool>(
                context,
                MaterialPageRoute(builder: (_) => const StatementImportScreen()),
              );
              if (result == true) {
                _loadTransactions(reset: true);
                _loadReviewQueueCount();
              }
            },
          ),

          const Spacer(),

          // Android only "Sync SMS" button
          if (PlatformCapabilities.supportsSms)
            FilledButton.tonalIcon(
              icon: const Icon(Icons.sync, size: 18),
              label: const Text('Sync SMS'),
              onPressed: () => _triggerSmsSync(silent: false),
            ),
        ],
      ),
    );
  }

  Widget _buildFilterBar() {
    final dateFormat = DateFormat('MM/dd');

    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      child: Row(
        children: [
          // Date Range Chip
          ActionChip(
            avatar: const Icon(Icons.calendar_today, size: 16),
            label: Text(
              _selectedDateRange != null
                  ? '${dateFormat.format(_selectedDateRange!.start)} - ${dateFormat.format(_selectedDateRange!.end)}'
                  : 'All Dates',
              style: const TextStyle(fontSize: 12),
            ),
            onPressed: _pickDateRange,
          ),
          if (_selectedDateRange != null)
            IconButton(
              icon: const Icon(Icons.close, size: 16),
              tooltip: 'Clear date filter',
              onPressed: () {
                setState(() => _selectedDateRange = null);
                _loadTransactions(reset: true);
              },
            ),
          const SizedBox(width: 8),

          // Category Dropdown
          DropdownButton<String?>(
            value: _selectedCategoryId,
            hint: const Text('Category', style: TextStyle(fontSize: 13)),
            underline: const SizedBox.shrink(),
            items: [
              const DropdownMenuItem<String?>(
                value: null,
                child: Text('All Categories', style: TextStyle(fontSize: 13)),
              ),
              ..._categories.map((c) => DropdownMenuItem<String?>(
                    value: c.id,
                    child: Text(c.name, style: const TextStyle(fontSize: 13)),
                  )),
            ],
            onChanged: (val) {
              setState(() => _selectedCategoryId = val);
              _loadTransactions(reset: true);
            },
          ),
          const SizedBox(width: 8),

          // Account Dropdown
          DropdownButton<String?>(
            value: _selectedBankAccountId,
            hint: const Text('Account', style: TextStyle(fontSize: 13)),
            underline: const SizedBox.shrink(),
            items: [
              const DropdownMenuItem<String?>(
                value: null,
                child: Text('All Accounts', style: TextStyle(fontSize: 13)),
              ),
              ..._bankAccounts.map((a) {
                final id = a['id'] as String;
                final nickname = a['nickname'] as String? ?? 'Account';
                final last4 = a['accountLast4'] as String? ?? '';
                return DropdownMenuItem<String?>(
                  value: id,
                  child: Text('$nickname (..$last4)', style: const TextStyle(fontSize: 13)),
                );
              }),
            ],
            onChanged: (val) {
              setState(() => _selectedBankAccountId = val);
              _loadTransactions(reset: true);
            },
          ),
          const SizedBox(width: 8),

          // Direction Chips (All / Debit / Credit)
          ChoiceChip(
            label: const Text('All', style: TextStyle(fontSize: 12)),
            selected: _selectedDirection == 'all',
            onSelected: (sel) {
              if (sel) {
                setState(() => _selectedDirection = 'all');
                _loadTransactions(reset: true);
              }
            },
          ),
          const SizedBox(width: 4),
          ChoiceChip(
            label: const Text('Debit', style: TextStyle(fontSize: 12)),
            selected: _selectedDirection == 'debit',
            onSelected: (sel) {
              if (sel) {
                setState(() => _selectedDirection = 'debit');
                _loadTransactions(reset: true);
              }
            },
          ),
          const SizedBox(width: 4),
          ChoiceChip(
            label: const Text('Credit', style: TextStyle(fontSize: 12)),
            selected: _selectedDirection == 'credit',
            onSelected: (sel) {
              if (sel) {
                setState(() => _selectedDirection = 'credit');
                _loadTransactions(reset: true);
              }
            },
          ),
        ],
      ),
    );
  }

  Widget _buildTransactionList() {
    if (_isLoading) {
      return const LoadingStateWidget();
    }

    if (_errorMessage != null) {
      return ErrorStateWidget(
        message: _errorMessage,
        onRetry: () => _loadTransactions(reset: true),
      );
    }

    if (_transactions.isEmpty) {
      return EmptyStateWidget(
        icon: Icons.receipt_long_outlined,
        message: 'No transactions found. Tap + to add one manually or Sync SMS.',
        actionLabel: 'Add Transaction',
        onAction: () async {
          final result = await Navigator.push<bool>(
            context,
            MaterialPageRoute(
              builder: (_) => const TransactionFormScreen(),
            ),
          );
          if (result == true) {
            _loadTransactions(reset: true);
            _loadReviewQueueCount();
          }
        },
      );
    }

    final dateFormat = DateFormat('MMM d, yyyy · HH:mm');

    return RefreshIndicator(
      onRefresh: () => _loadTransactions(reset: true),
      child: ListView.separated(
        controller: _scrollController,
        padding: const EdgeInsets.symmetric(vertical: 8),
        itemCount: _transactions.length + (_isLoadingMore ? 1 : 0),
        separatorBuilder: (_, __) => const Divider(height: 1, indent: 72),
        itemBuilder: (context, index) {
        if (index == _transactions.length) {
          return const Padding(
            padding: EdgeInsets.symmetric(vertical: 16),
            child: Center(child: CircularProgressIndicator(strokeWidth: 2)),
          );
        }

        final tx = _transactions[index];
        final isDebit = tx.direction == 'debit';
        final currencySymbol = _getCurrencySymbol(tx.currencyId);
        final categoryName = _getCategoryName(tx.categoryId);
        final merchant = (tx.merchantRaw != null && tx.merchantRaw!.trim().isNotEmpty)
            ? tx.merchantRaw!
            : 'No merchant';

        final hasReviewBadge = tx.reviewStatus != 'none';
        final hasSelfTransferBadge = tx.isSelfTransfer;

        return ListTile(
          onTap: () async {
            final result = await Navigator.push<bool>(
              context,
              MaterialPageRoute(
                builder: (_) => TransactionFormScreen(initialTransaction: tx),
              ),
            );
            if (result == true) {
              _loadTransactions(reset: true);
              _loadReviewQueueCount();
            }
          },
          leading: CircleAvatar(
            backgroundColor: isDebit ? Colors.red.withOpacity(0.12) : Colors.green.withOpacity(0.12),
            child: Icon(
              isDebit ? Icons.arrow_downward : Icons.arrow_upward,
              color: isDebit ? Colors.redAccent : Colors.greenAccent,
              size: 20,
            ),
          ),
          title: Row(
            children: [
              Expanded(
                child: Text(
                  merchant,
                  style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 15),
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              if (hasReviewBadge) ...[
                const SizedBox(width: 6),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(
                    color: Colors.amber.withOpacity(0.2),
                    borderRadius: BorderRadius.circular(4),
                    border: Border.all(color: Colors.amber, width: 0.8),
                  ),
                  child: const Text(
                    'Review',
                    style: TextStyle(fontSize: 10, color: Colors.amber, fontWeight: FontWeight.bold),
                  ),
                ),
              ],
              if (hasSelfTransferBadge) ...[
                const SizedBox(width: 6),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(
                    color: Colors.cyan.withOpacity(0.2),
                    borderRadius: BorderRadius.circular(4),
                    border: Border.all(color: Colors.cyan, width: 0.8),
                  ),
                  child: const Text(
                    'Transfer',
                    style: TextStyle(fontSize: 10, color: Colors.cyan, fontWeight: FontWeight.bold),
                  ),
                ),
              ],
            ],
          ),
          subtitle: Row(
            children: [
              Text(
                categoryName,
                style: const TextStyle(fontSize: 12, color: Colors.white70),
              ),
              const Text(' · ', style: TextStyle(color: Colors.white38)),
              Text(
                dateFormat.format(tx.transactionDate.toLocal()),
                style: const TextStyle(fontSize: 11, color: Colors.white54),
              ),
            ],
          ),
          trailing: Text(
            '${isDebit ? '-' : '+'}$currencySymbol${tx.amountFormatted}',
            style: TextStyle(
              fontSize: 15,
              fontWeight: FontWeight.bold,
              color: isDebit ? Colors.redAccent : Colors.greenAccent,
            ),
          ),
        );
      },
    ),
  );
}
}

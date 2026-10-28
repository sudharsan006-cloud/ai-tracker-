import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import '../auth/auth_service.dart';
import '../budgets/budget_alert_service.dart';
import 'expense_repository.dart';
import 'models.dart';

class TransactionFormScreen extends StatefulWidget {
  final Transaction? initialTransaction;
  final String? unparsedSmsId;
  final DateTime? initialDate;

  const TransactionFormScreen({
    super.key,
    this.initialTransaction,
    this.unparsedSmsId,
    this.initialDate,
  });

  @override
  State<TransactionFormScreen> createState() => _TransactionFormScreenState();
}

class _TransactionFormScreenState extends State<TransactionFormScreen> {
  late ExpenseRepository _repository;
  bool _isLoading = true;
  bool _isSaving = false;
  String? _errorMessage;

  List<Map<String, dynamic>> _bankAccounts = [];
  List<Category> _categories = [];

  String? _selectedAccountId;
  String _selectedDirection = 'debit';
  final _amountController = TextEditingController();
  final _merchantController = TextEditingController();
  String? _selectedCategoryId;
  late DateTime _selectedDateTime;

  bool get isEditing => widget.initialTransaction != null;
  bool get isResolvingSms => widget.unparsedSmsId != null;

  bool get isRestrictedEdit {
    if (!isEditing) return false;
    final tx = widget.initialTransaction!;
    final isLinked = tx.isSelfTransfer || tx.refundMatchedId != null;
    final hasReviewFlag = tx.reviewStatus != 'none';
    return isLinked || hasReviewFlag;
  }

  @override
  void initState() {
    super.initState();
    _repository = ExpenseRepository(context.read<AuthService>().apiClient);

    if (isEditing) {
      final tx = widget.initialTransaction!;
      _selectedAccountId = tx.bankAccountId;
      _selectedDirection = tx.direction;
      _amountController.text = tx.amountFormatted;
      _merchantController.text = tx.merchantRaw ?? '';
      _selectedCategoryId = tx.categoryId;
      _selectedDateTime = tx.transactionDate.toLocal();
    } else {
      _selectedDateTime = widget.initialDate?.toLocal() ?? DateTime.now();
    }

    _loadData();
  }

  Future<void> _loadData() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final futures = await Future.wait([
        _repository.getBankAccounts(),
        _repository.getCategories(),
      ]);

      if (mounted) {
        setState(() {
          _bankAccounts = (futures[0] as List<Map<String, dynamic>>)
              .where((a) => a['isActive'] == true)
              .toList();
          _categories = futures[1] as List<Category>;

          if (!isEditing && _selectedAccountId == null && _bankAccounts.isNotEmpty) {
            _selectedAccountId = _bankAccounts.first['id'] as String;
          }
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

  Future<void> _pickDateTime() async {
    final pickedDate = await showDatePicker(
      context: context,
      initialDate: _selectedDateTime,
      firstDate: DateTime(2000),
      lastDate: DateTime.now().add(const Duration(days: 1)),
    );
    if (pickedDate == null || !mounted) return;

    final pickedTime = await showTimePicker(
      context: context,
      initialTime: TimeOfDay.fromDateTime(_selectedDateTime),
    );
    if (pickedTime == null || !mounted) return;

    setState(() {
      _selectedDateTime = DateTime(
        pickedDate.year,
        pickedDate.month,
        pickedDate.day,
        pickedTime.hour,
        pickedTime.minute,
      );
    });
  }

  Future<void> _save() async {
    setState(() {
      _errorMessage = null;
    });

    if (_selectedAccountId == null) {
      setState(() => _errorMessage = 'Please select a bank account');
      return;
    }

    final amountText = _amountController.text.trim();
    final amount = num.tryParse(amountText);
    if (amount == null || amount <= 0) {
      setState(() => _errorMessage = 'Amount must be greater than 0');
      return;
    }

    final parts = amountText.split('.');
    if (parts.length > 1 && parts[1].length > 2) {
      setState(() => _errorMessage = 'Amount can have at most 2 decimal places');
      return;
    }

    setState(() => _isSaving = true);

    try {
      if (isResolvingSms) {
        await _repository.resolveUnparsedSms(
          widget.unparsedSmsId!,
          bankAccountId: _selectedAccountId!,
          amount: amount,
          direction: _selectedDirection,
          transactionDate: _selectedDateTime,
          merchant: _merchantController.text.trim().isNotEmpty ? _merchantController.text.trim() : null,
          categoryId: _selectedCategoryId,
        );
      } else if (isEditing) {
        if (isRestrictedEdit) {
          await _repository.patchTransaction(
            widget.initialTransaction!.id,
            merchant: _merchantController.text.trim().isNotEmpty ? _merchantController.text.trim() : null,
            categoryId: _selectedCategoryId,
          );
        } else {
          await _repository.patchTransaction(
            widget.initialTransaction!.id,
            bankAccountId: _selectedAccountId,
            amount: amount,
            direction: _selectedDirection,
            transactionDate: _selectedDateTime,
            merchant: _merchantController.text.trim().isNotEmpty ? _merchantController.text.trim() : null,
            categoryId: _selectedCategoryId,
          );
        }
      } else {
        await _repository.createTransaction(
          bankAccountId: _selectedAccountId!,
          amount: amount,
          direction: _selectedDirection,
          merchant: _merchantController.text.trim().isNotEmpty ? _merchantController.text.trim() : null,
          transactionDate: _selectedDateTime,
          categoryId: _selectedCategoryId,
        );
        // B3: Check budget alerts after saving manual expense
        BudgetAlertService.checkBudgetsAndNotify(_repository);
      }

      if (mounted) {
        Navigator.pop(context, true);
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _errorMessage = e.toString().replaceFirst('Exception: ', '');
          _isSaving = false;
        });
      }
    }
  }

  Future<void> _delete() async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Delete Transaction'),
        content: const Text('Are you sure you want to delete this transaction?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: Colors.red),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Delete'),
          ),
        ],
      ),
    );

    if (confirm != true || !mounted) return;

    try {
      await _repository.deleteTransaction(widget.initialTransaction!.id);
      if (mounted) {
        Navigator.pop(context, true);
      }
    } catch (e) {
      if (mounted) {
        setState(() => _errorMessage = e.toString().replaceFirst('Exception: ', ''));
      }
    }
  }

  Future<void> _unlink() async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Unlink Transaction'),
        content: const Text('Are you sure you want to unlink this match?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Unlink'),
          ),
        ],
      ),
    );

    if (confirm != true || !mounted) return;

    try {
      await _repository.unlinkTransaction(widget.initialTransaction!.id);
      if (mounted) {
        Navigator.pop(context, true);
      }
    } catch (e) {
      if (mounted) {
        setState(() => _errorMessage = e.toString().replaceFirst('Exception: ', ''));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final title = isResolvingSms
        ? 'Resolve SMS Transaction'
        : isEditing
            ? 'Edit Transaction'
            : 'Add Transaction';

    final isLinked = isEditing &&
        (widget.initialTransaction!.isSelfTransfer || widget.initialTransaction!.refundMatchedId != null);

    return Scaffold(
      appBar: AppBar(
        title: Text(title),
        actions: [
          if (isEditing && isLinked)
            IconButton(
              icon: const Icon(Icons.link_off),
              tooltip: 'Unlink Match',
              onPressed: _unlink,
            ),
          if (isEditing)
            IconButton(
              icon: const Icon(Icons.delete_outline, color: Colors.redAccent),
              tooltip: 'Delete',
              onPressed: _delete,
            ),
        ],
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : SingleChildScrollView(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  if (isRestrictedEdit) ...[
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: Colors.amber.withOpacity(0.15),
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(color: Colors.amber.withOpacity(0.5)),
                      ),
                      child: const Row(
                        children: [
                          Icon(Icons.info_outline, color: Colors.amber),
                          SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              'Unlink or resolve the match first to modify amount, account, or date.',
                              style: TextStyle(color: Colors.amber, fontSize: 13),
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 16),
                  ],

                  // Account dropdown
                  DropdownButtonFormField<String>(
                    value: _selectedAccountId,
                    decoration: const InputDecoration(labelText: 'Bank Account'),
                    items: _bankAccounts.map((a) {
                      final id = a['id'] as String;
                      final nickname = a['nickname'] as String? ?? 'Account';
                      final last4 = a['accountLast4'] as String? ?? '';
                      return DropdownMenuItem<String>(
                        value: id,
                        child: Text('$nickname (..$last4)'),
                      );
                    }).toList(),
                    onChanged: isRestrictedEdit ? null : (val) => setState(() => _selectedAccountId = val),
                  ),
                  const SizedBox(height: 16),

                  // Direction toggle (Debit / Credit)
                  SegmentedButton<String>(
                    segments: const [
                      ButtonSegment(value: 'debit', label: Text('Debit (-)'), icon: Icon(Icons.arrow_downward, color: Colors.redAccent)),
                      ButtonSegment(value: 'credit', label: Text('Credit (+)'), icon: Icon(Icons.arrow_upward, color: Colors.greenAccent)),
                    ],
                    selected: {_selectedDirection},
                    onSelectionChanged: isRestrictedEdit ? null : (set) => setState(() => _selectedDirection = set.first),
                  ),
                  const SizedBox(height: 16),

                  // Amount
                  TextField(
                    controller: _amountController,
                    enabled: !isRestrictedEdit,
                    decoration: const InputDecoration(
                      labelText: 'Amount',
                      hintText: '0.00',
                      prefixIcon: Icon(Icons.payments_outlined),
                    ),
                    keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  ),
                  const SizedBox(height: 16),

                  // Merchant (editable always)
                  TextField(
                    controller: _merchantController,
                    decoration: const InputDecoration(
                      labelText: 'Merchant (Optional)',
                      hintText: 'e.g. Swiggy, Amazon',
                      prefixIcon: Icon(Icons.storefront_outlined),
                    ),
                  ),
                  const SizedBox(height: 16),

                  // Category dropdown (editable always)
                  DropdownButtonFormField<String?>(
                    value: _selectedCategoryId,
                    decoration: const InputDecoration(
                      labelText: 'Category (Optional)',
                      prefixIcon: Icon(Icons.category_outlined),
                    ),
                    items: [
                      const DropdownMenuItem<String?>(
                        value: null,
                        child: Text('Uncategorized'),
                      ),
                      ..._categories.map((c) => DropdownMenuItem<String?>(
                            value: c.id,
                            child: Text(c.name),
                          )),
                    ],
                    onChanged: (val) => setState(() => _selectedCategoryId = val),
                  ),
                  const SizedBox(height: 16),

                  // Date and time picker
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: const Icon(Icons.calendar_today_outlined),
                    title: const Text('Date & Time'),
                    subtitle: Text(DateFormat('yyyy-MM-dd HH:mm').format(_selectedDateTime)),
                    trailing: isRestrictedEdit
                        ? null
                        : IconButton(
                            icon: const Icon(Icons.edit_calendar),
                            onPressed: _pickDateTime,
                          ),
                    onTap: isRestrictedEdit ? null : _pickDateTime,
                  ),
                  const SizedBox(height: 24),

                  if (_errorMessage != null) ...[
                    Text(
                      _errorMessage!,
                      style: const TextStyle(color: Colors.redAccent, fontSize: 14),
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 16),
                  ],

                  FilledButton(
                    onPressed: _isSaving ? null : _save,
                    child: _isSaving
                        ? const SizedBox(height: 20, width: 20, child: CircularProgressIndicator(strokeWidth: 2))
                        : Text(isEditing ? 'Save Changes' : 'Save Transaction'),
                  ),
                ],
              ),
            ),
    );
  }
}

import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import '../auth/auth_service.dart';
import 'models.dart';
import 'statement_repository.dart';

class StatementReviewScreen extends StatefulWidget {
  final String importId;

  const StatementReviewScreen({super.key, required this.importId});

  @override
  State<StatementReviewScreen> createState() => _StatementReviewScreenState();
}

class _StatementReviewScreenState extends State<StatementReviewScreen> {
  late final StatementRepository _repository;

  StatementImport? _summary;
  List<StatementRow> _rows = [];
  bool _isLoading = true;
  bool _isCommitting = false;
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    final apiClient = context.read<AuthService>().apiClient;
    _repository = StatementRepository(apiClient);
    _loadData();
  }

  Future<void> _loadData() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final summary = await _repository.getImport(widget.importId);
      final rows = await _repository.getRows(widget.importId);

      if (mounted) {
        setState(() {
          _summary = summary;
          _rows = rows;
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

  Future<void> _toggleRowSelection(StatementRow row, bool? newSelected) async {
    if (newSelected == null) return;

    try {
      final updatedRow = await _repository.patchRow(widget.importId, row.id, {
        'selected': newSelected,
      });

      // Reload summary to get authoritative backend counts
      final updatedSummary = await _repository.getImport(widget.importId);

      if (mounted) {
        setState(() {
          _summary = updatedSummary;
          final idx = _rows.indexWhere((r) => r.id == row.id);
          if (idx != -1) {
            _rows[idx] = updatedRow;
          }
        });
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to update row: ${e.toString().replaceFirst("Exception: ", "")}')),
        );
      }
    }
  }

  Future<void> _openEditDialog(StatementRow row) async {
    final dateController = TextEditingController(text: row.txnDate ?? '');
    final descController = TextEditingController(text: row.description ?? '');
    final amountController = TextEditingController(
      text: row.amount != null ? row.amount!.toStringAsFixed(2) : '',
    );
    String selectedDirection = row.direction?.toLowerCase() == 'credit' ? 'credit' : 'debit';

    final saved = await showDialog<bool>(
      context: context,
      builder: (ctx) {
        return StatefulBuilder(
          builder: (context, setDialogState) {
            return AlertDialog(
              title: const Text('Edit Transaction Row'),
              content: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    // Date
                    TextField(
                      controller: dateController,
                      decoration: const InputDecoration(
                        labelText: 'Date (yyyy-MM-dd)',
                        suffixIcon: Icon(Icons.calendar_today),
                      ),
                      readOnly: true,
                      onTap: () async {
                        DateTime initialDate = DateTime.now();
                        if (dateController.text.isNotEmpty) {
                          final parsed = DateTime.tryParse(dateController.text);
                          if (parsed != null) initialDate = parsed;
                        }
                        final picked = await showDatePicker(
                          context: context,
                          initialDate: initialDate,
                          firstDate: DateTime(2000),
                          lastDate: DateTime(2100),
                        );
                        if (picked != null) {
                          setDialogState(() {
                            dateController.text = DateFormat('yyyy-MM-dd').format(picked);
                          });
                        }
                      },
                    ),
                    const SizedBox(height: 12),

                    // Description
                    TextField(
                      controller: descController,
                      decoration: const InputDecoration(labelText: 'Description'),
                      maxLength: 255,
                    ),
                    const SizedBox(height: 8),

                    // Amount
                    TextField(
                      controller: amountController,
                      decoration: const InputDecoration(labelText: 'Amount'),
                      keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    ),
                    const SizedBox(height: 16),

                    // Direction Toggle
                    Row(
                      children: [
                        const Text('Type: ', style: TextStyle(fontWeight: FontWeight.w600)),
                        const SizedBox(width: 8),
                        ChoiceChip(
                          label: const Text('Debit (Expense)'),
                          selected: selectedDirection == 'debit',
                          selectedColor: Colors.red.withOpacity(0.2),
                          onSelected: (val) {
                            if (val) setDialogState(() => selectedDirection = 'debit');
                          },
                        ),
                        const SizedBox(width: 8),
                        ChoiceChip(
                          label: const Text('Credit (Income)'),
                          selected: selectedDirection == 'credit',
                          selectedColor: Colors.green.withOpacity(0.2),
                          onSelected: (val) {
                            if (val) setDialogState(() => selectedDirection = 'credit');
                          },
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.pop(ctx, false),
                  child: const Text('Cancel'),
                ),
                FilledButton(
                  onPressed: () {
                    // Quick validation
                    final amt = double.tryParse(amountController.text.trim());
                    if (amt == null || amt <= 0) {
                      ScaffoldMessenger.of(ctx).showSnackBar(
                        const SnackBar(content: Text('Please enter a valid positive amount')),
                      );
                      return;
                    }
                    Navigator.pop(ctx, true);
                  },
                  child: const Text('Save'),
                ),
              ],
            );
          },
        );
      },
    );

    if (saved == true) {
      try {
        final patchData = <String, dynamic>{
          'txnDate': dateController.text.trim(),
          'description': descController.text.trim(),
          'amount': double.parse(amountController.text.trim()),
          'direction': selectedDirection,
        };

        await _repository.patchRow(widget.importId, row.id, patchData);
        await _loadData();
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Failed to update: ${e.toString().replaceFirst("Exception: ", "")}')),
          );
        }
      }
    }
  }

  Future<void> _handleCommit() async {
    setState(() => _isCommitting = true);

    try {
      final result = await _repository.commit(widget.importId);
      final created = result['created'] as int? ?? 0;

      if (mounted) {
        final message = created > 0
            ? 'Imported $created transactions. Some may need review'
            : 'Imported $created transactions';

        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(message)),
        );
        Navigator.pop(context, true);
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isCommitting = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(e.toString().replaceFirst('Exception: ', '')),
            backgroundColor: Colors.redAccent,
          ),
        );
      }
    }
  }

  Future<void> _handleDiscard() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Discard Import?'),
        content: const Text('All staged rows from this statement will be deleted. This cannot be undone.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: Colors.red),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Discard'),
          ),
        ],
      ),
    );

    if (confirmed == true) {
      try {
        await _repository.discard(widget.importId);
        if (mounted) {
          Navigator.pop(context, false);
        }
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Failed to discard: $e')),
          );
        }
      }
    }
  }

  String _reasonText(String? reason) {
    switch (reason) {
      case 'parse_error':
        return 'Could not read this line';
      case 'missing_fields':
        return 'Something is missing';
      case 'balance_mismatch':
        return 'Does not match the balance';
      case 'low_confidence':
        return 'Scan was unclear';
      default:
        return 'Needs check';
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Review Statement'),
        actions: [
          IconButton(
            icon: const Icon(Icons.delete_outline),
            tooltip: 'Discard',
            onPressed: _isLoading || _isCommitting ? null : _handleDiscard,
          ),
        ],
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : _errorMessage != null
              ? Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(Icons.error_outline, size: 48, color: Colors.redAccent),
                      const SizedBox(height: 12),
                      Text(_errorMessage!, style: const TextStyle(color: Colors.white70)),
                      const SizedBox(height: 16),
                      FilledButton(onPressed: _loadData, child: const Text('Retry')),
                    ],
                  ),
                )
              : Column(
                  children: [
                    _buildHeaderBanner(),
                    const Divider(height: 1),
                    Expanded(child: _buildRowList()),
                    _buildBottomBar(),
                  ],
                ),
    );
  }

  Widget _buildHeaderBanner() {
    if (_summary == null) return const SizedBox.shrink();

    final gateStatus = _summary!.gateStatus;
    final rowCount = _summary!.rowCount ?? _rows.length;
    final flaggedCount = _summary!.flaggedCount ?? 0;

    Widget? banner;
    if (gateStatus == 'failed') {
      banner = Container(
        margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: Colors.red.withOpacity(0.15),
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: Colors.red.withOpacity(0.4)),
        ),
        child: const Row(
          children: [
            Icon(Icons.warning_amber_rounded, color: Colors.redAccent, size: 20),
            SizedBox(width: 8),
            Expanded(
              child: Text(
                'Totals do not add up. Check the highlighted rows.',
                style: TextStyle(color: Colors.redAccent, fontSize: 13, fontWeight: FontWeight.w600),
              ),
            ),
          ],
        ),
      );
    } else if (gateStatus == 'not_checkable') {
      banner = Container(
        margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: Colors.amber.withOpacity(0.15),
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: Colors.amber.withOpacity(0.4)),
        ),
        child: const Row(
          children: [
            Icon(Icons.info_outline, color: Colors.amber, size: 20),
            SizedBox(width: 8),
            Expanded(
              child: Text(
                'This statement could not be checked against balances. Review all rows carefully.',
                style: TextStyle(color: Colors.amber, fontSize: 13),
              ),
            ),
          ],
        ),
      );
    } else if (gateStatus == 'passed') {
      banner = Container(
        margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        padding: const EdgeInsets.all(10),
        decoration: BoxDecoration(
          color: Colors.green.withOpacity(0.12),
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: Colors.green.withOpacity(0.3)),
        ),
        child: const Row(
          children: [
            Icon(Icons.check_circle_outline, color: Colors.green, size: 18),
            SizedBox(width: 8),
            Text(
              'Balance verified: statement totals match.',
              style: TextStyle(color: Colors.green, fontSize: 13),
            ),
          ],
        ),
      );
    }

    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                '$rowCount Rows Found',
                style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
              ),
              if (flaggedCount > 0)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: Colors.orange.withOpacity(0.2),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Text(
                    '$flaggedCount need check',
                    style: const TextStyle(color: Colors.orange, fontSize: 12, fontWeight: FontWeight.bold),
                  ),
                ),
            ],
          ),
        ),
        if (banner != null) banner,
      ],
    );
  }

  Widget _buildRowList() {
    if (_rows.isEmpty) {
      return const Center(child: Text('No rows in this statement'));
    }

    return ListView.separated(
      itemCount: _rows.length,
      separatorBuilder: (_, __) => const Divider(height: 1),
      itemBuilder: (context, index) {
        final row = _rows[index];
        final isCredit = row.direction?.toLowerCase() == 'credit';
        final amountText = row.amount != null
            ? '${isCredit ? "+" : "-"} ₹${row.amount!.toStringAsFixed(2)}'
            : '--';
        final amountColor = isCredit ? Colors.green : Colors.redAccent;

        final hasExistingMatch = row.existingTransaction != null;

        Color tileColor = Colors.transparent;
        if (row.needsCheck) {
          tileColor = Colors.orange.withOpacity(0.12);
        }

        return Container(
          color: tileColor,
          child: ListTile(
            contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
            leading: Checkbox(
              value: row.selected,
              onChanged: (val) => _toggleRowSelection(row, val),
            ),
            title: Row(
              children: [
                Expanded(
                  child: Text(
                    row.description != null && row.description!.isNotEmpty
                        ? row.description!
                        : '(No description)',
                    style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w500),
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                Text(
                  amountText,
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.bold,
                    color: amountColor,
                  ),
                ),
              ],
            ),
            subtitle: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const SizedBox(height: 2),
                Row(
                  children: [
                    Text(
                      row.txnDate ?? 'No date',
                      style: const TextStyle(fontSize: 12, color: Colors.white60),
                    ),
                    if (row.balanceAfter != null) ...[
                      const SizedBox(width: 8),
                      Text(
                        'Bal: ₹${row.balanceAfter!.toStringAsFixed(2)}',
                        style: const TextStyle(fontSize: 11, color: Colors.white38),
                      ),
                    ],
                  ],
                ),
                if (row.needsCheck) ...[
                  const SizedBox(height: 4),
                  Row(
                    children: [
                      const Icon(Icons.warning_amber, size: 14, color: Colors.orange),
                      const SizedBox(width: 4),
                      Text(
                        _reasonText(row.checkReason),
                        style: const TextStyle(color: Colors.orange, fontSize: 12, fontWeight: FontWeight.w500),
                      ),
                    ],
                  ),
                ],
                if (hasExistingMatch) ...[
                  const SizedBox(height: 4),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                    decoration: BoxDecoration(
                      color: Colors.white.withOpacity(0.06),
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: Text(
                      'Looks like it is already in your list (${row.existingTransaction!.merchantRaw ?? "Transaction"} ₹${row.existingTransaction!.amount.toStringAsFixed(2)})',
                      style: const TextStyle(color: Colors.white54, fontSize: 11),
                    ),
                  ),
                ],
              ],
            ),
            onTap: () => _openEditDialog(row),
          ),
        );
      },
    );
  }

  Widget _buildBottomBar() {
    if (_summary == null) return const SizedBox.shrink();

    final selectedCount = _summary!.selectedCount;
    final blockingCount = _summary!.blockingCount;
    final isBlocked = blockingCount > 0;

    return SafeArea(
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: Theme.of(context).cardColor,
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.2),
              blurRadius: 4,
              offset: const Offset(0, -2),
            ),
          ],
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            FilledButton(
              onPressed: isBlocked || _isCommitting ? null : _handleCommit,
              style: FilledButton.styleFrom(
                padding: const EdgeInsets.symmetric(vertical: 14),
              ),
              child: _isCommitting
                  ? const SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                    )
                  : Text(
                      'Import $selectedCount transactions',
                      style: const TextStyle(fontSize: 16),
                    ),
            ),
            if (isBlocked) ...[
              const SizedBox(height: 6),
              Text(
                'Fix or uncheck $blockingCount highlighted rows',
                textAlign: TextAlign.center,
                style: const TextStyle(color: Colors.orange, fontSize: 12),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

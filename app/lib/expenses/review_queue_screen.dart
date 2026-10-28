import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import '../auth/auth_service.dart';
import 'expense_repository.dart';
import 'models.dart';
import 'transaction_form_screen.dart';

class ReviewQueueScreen extends StatefulWidget {
  const ReviewQueueScreen({super.key});

  @override
  State<ReviewQueueScreen> createState() => _ReviewQueueScreenState();
}

class _ReviewQueueScreenState extends State<ReviewQueueScreen> {
  late ExpenseRepository _repository;
  bool _isLoading = true;
  String? _errorMessage;
  ReviewQueue? _queue;

  @override
  void initState() {
    super.initState();
    _repository = ExpenseRepository(context.read<AuthService>().apiClient);
    _loadQueue();
  }

  Future<void> _loadQueue() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final queue = await _repository.getReviewQueue();
      if (mounted) {
        setState(() {
          _queue = queue;
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

  Future<void> _confirmAction(String txId) async {
    try {
      await _repository.confirmReview(txId);
      _loadQueue();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(e.toString().replaceFirst('Exception: ', ''))),
        );
      }
    }
  }

  Future<void> _dismissAction(String txId) async {
    try {
      await _repository.dismissReview(txId);
      _loadQueue();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(e.toString().replaceFirst('Exception: ', ''))),
        );
      }
    }
  }

  Future<void> _deleteDuplicate(String txIdToDelete) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Delete Duplicate'),
        content: const Text('Delete this duplicate transaction and keep the other?'),
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

    if (confirm == true) {
      _confirmAction(txIdToDelete);
    }
  }

  Future<void> _discardSms(String id) async {
    try {
      await _repository.discardUnparsedSms(id);
      _loadQueue();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(e.toString().replaceFirst('Exception: ', ''))),
        );
      }
    }
  }

  Future<void> _resolveSms(UnparsedSmsItem sms) async {
    final result = await Navigator.push<bool>(
      context,
      MaterialPageRoute(
        builder: (_) => TransactionFormScreen(
          unparsedSmsId: sms.id,
          initialDate: sms.receivedAt,
        ),
      ),
    );

    if (result == true) {
      _loadQueue();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Review Queue'),
      ),
      body: _buildBody(),
    );
  }

  Widget _buildBody() {
    if (_isLoading) {
      return const Center(child: CircularProgressIndicator());
    }

    if (_errorMessage != null) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text(_errorMessage!, style: const TextStyle(color: Colors.redAccent)),
            const SizedBox(height: 12),
            FilledButton(onPressed: _loadQueue, child: const Text('Retry')),
          ],
        ),
      );
    }

    final queue = _queue;
    if (queue == null || queue.totalCount == 0) {
      return const Center(child: Text('Nothing to review'));
    }

    return ListView(
      padding: const EdgeInsets.all(12),
      children: [
        if (queue.duplicates.isNotEmpty) ...[
          _buildSectionHeader('Possible Duplicates (${queue.duplicates.length})'),
          ...queue.duplicates.map(_buildDuplicateCard),
          const SizedBox(height: 16),
        ],
        if (queue.selfTransfers.isNotEmpty) ...[
          _buildSectionHeader('Possible Self-Transfers (${queue.selfTransfers.length})'),
          ...queue.selfTransfers.map(_buildSelfTransferCard),
          const SizedBox(height: 16),
        ],
        if (queue.refundMatches.isNotEmpty) ...[
          _buildSectionHeader('Possible Refunds (${queue.refundMatches.length})'),
          ...queue.refundMatches.map(_buildRefundCard),
          const SizedBox(height: 16),
        ],
        if (queue.unparsedSms.isNotEmpty) ...[
          _buildSectionHeader('SMS to Review (${queue.unparsedSms.length})'),
          ...queue.unparsedSms.map(_buildSmsCard),
          const SizedBox(height: 16),
        ],
      ],
    );
  }

  Widget _buildSectionHeader(String title) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8, top: 4),
      child: Text(
        title,
        style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: Colors.tealAccent),
      ),
    );
  }

  Widget _buildDuplicateCard(TransactionPair pair) {
    final t1 = pair.transaction;
    final t2 = pair.partner;
    final dateFormat = DateFormat('yyyy-MM-dd HH:mm');

    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: Colors.white.withOpacity(0.05),
                      borderRadius: BorderRadius.circular(6),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(t1.merchantRaw ?? 'No merchant', style: const TextStyle(fontWeight: FontWeight.bold)),
                        Text('${t1.direction.toUpperCase()} ${t1.amountFormatted}', style: const TextStyle(color: Colors.redAccent)),
                        Text(dateFormat.format(t1.transactionDate.toLocal()), style: const TextStyle(fontSize: 11, color: Colors.white60)),
                        const SizedBox(height: 8),
                        OutlinedButton(
                          onPressed: () => _deleteDuplicate(t1.id),
                          style: OutlinedButton.styleFrom(visualDensity: VisualDensity.compact),
                          child: const Text('Delete this one', style: TextStyle(fontSize: 12)),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: Colors.white.withOpacity(0.05),
                      borderRadius: BorderRadius.circular(6),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(t2.merchantRaw ?? 'No merchant', style: const TextStyle(fontWeight: FontWeight.bold)),
                        Text('${t2.direction.toUpperCase()} ${t2.amountFormatted}', style: const TextStyle(color: Colors.redAccent)),
                        Text(dateFormat.format(t2.transactionDate.toLocal()), style: const TextStyle(fontSize: 11, color: Colors.white60)),
                        const SizedBox(height: 8),
                        OutlinedButton(
                          onPressed: () => _deleteDuplicate(t2.id),
                          style: OutlinedButton.styleFrom(visualDensity: VisualDensity.compact),
                          child: const Text('Delete this one', style: TextStyle(fontSize: 12)),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            TextButton(
              onPressed: () => _dismissAction(t1.id),
              child: const Text('Keep both (Not a duplicate)'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSelfTransferCard(TransactionPair pair) {
    final t1 = pair.transaction;
    final t2 = pair.partner;
    final dateFormat = DateFormat('yyyy-MM-dd HH:mm');

    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    '${t1.direction.toUpperCase()}: ${t1.amountFormatted} (${dateFormat.format(t1.transactionDate.toLocal())})',
                    style: TextStyle(
                      color: t1.direction == 'debit' ? Colors.redAccent : Colors.greenAccent,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 4),
            Row(
              children: [
                Expanded(
                  child: Text(
                    '${t2.direction.toUpperCase()}: ${t2.amountFormatted} (${dateFormat.format(t2.transactionDate.toLocal())})',
                    style: TextStyle(
                      color: t2.direction == 'debit' ? Colors.redAccent : Colors.greenAccent,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                TextButton(
                  onPressed: () => _dismissAction(t1.id),
                  child: const Text('No'),
                ),
                const SizedBox(width: 8),
                FilledButton(
                  onPressed: () => _confirmAction(t1.id),
                  child: const Text('Yes, same money moved'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildRefundCard(TransactionPair pair) {
    final credit = pair.transaction;
    final debit = pair.partner;
    final dateFormat = DateFormat('yyyy-MM-dd');

    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Credit: +${credit.amountFormatted} from ${credit.merchantRaw ?? "Unknown"} (${dateFormat.format(credit.transactionDate.toLocal())})',
              style: const TextStyle(color: Colors.greenAccent, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 4),
            Text(
              'Original Debit: -${debit.amountFormatted} from ${debit.merchantRaw ?? "Unknown"} (${dateFormat.format(debit.transactionDate.toLocal())})',
              style: const TextStyle(color: Colors.redAccent),
            ),
            const SizedBox(height: 12),
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                TextButton(
                  onPressed: () => _dismissAction(credit.id),
                  child: const Text('No'),
                ),
                const SizedBox(width: 8),
                FilledButton(
                  onPressed: () => _confirmAction(credit.id),
                  child: const Text("Yes, it's a refund"),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSmsCard(UnparsedSmsItem sms) {
    final dateFormat = DateFormat('yyyy-MM-dd HH:mm');

    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  sms.sender ?? 'Unknown Sender',
                  style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13, color: Colors.white70),
                ),
                Text(
                  dateFormat.format(sms.receivedAt.toLocal()),
                  style: const TextStyle(fontSize: 12, color: Colors.white54),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Text(sms.rawText, style: const TextStyle(fontSize: 13)),
            const SizedBox(height: 12),
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                TextButton(
                  onPressed: () => _discardSms(sms.id),
                  child: const Text('Discard', style: TextStyle(color: Colors.white60)),
                ),
                const SizedBox(width: 8),
                FilledButton(
                  onPressed: () => _resolveSms(sms),
                  child: const Text('Add transaction'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

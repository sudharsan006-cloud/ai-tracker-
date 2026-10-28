import 'package:shared_preferences/shared_preferences.dart';
import '../budgets/budget_alert_service.dart';
import '../core/platform_capabilities.dart';
import '../expenses/expense_repository.dart';
import 'raw_sms.dart';
import 'sms_reader.dart';

class SyncResult {
  final int received;
  final int created;
  final int queued;
  final int ignored;
  final int duplicates;

  SyncResult({
    required this.received,
    required this.created,
    required this.queued,
    required this.ignored,
    required this.duplicates,
  });

  SyncResult operator +(SyncResult other) {
    return SyncResult(
      received: received + other.received,
      created: created + other.created,
      queued: queued + other.queued,
      ignored: ignored + other.ignored,
      duplicates: duplicates + other.duplicates,
    );
  }
}

class SmsSyncService {
  final ExpenseRepository _expenseRepository;

  SmsSyncService(this._expenseRepository);

  Future<SyncResult?> sync() async {
    // 1. If !PlatformCapabilities.supportsSms -> return null
    if (!PlatformCapabilities.supportsSms) {
      return null;
    }

    // 2. If !await ensureSmsPermission() -> return null
    final hasPermission = await ensureSmsPermission();
    if (!hasPermission) {
      return null;
    }

    final prefs = await SharedPreferences.getInstance();
    final int? lastSyncMs = prefs.getInt('sms_last_sync_ms');

    // 3. since date: if missing, now minus 30 days
    final DateTime since = lastSyncMs != null
        ? DateTime.fromMillisecondsSinceEpoch(lastSyncMs)
        : DateTime.now().subtract(const Duration(days: 30));

    // 4. read inbox and keep only messages with at least one letter in sender
    final List<RawSms> rawMessages = await readInboxSince(since);
    final alphanumericRegex = RegExp(r'[A-Za-z]');
    final List<RawSms> bankMessages = rawMessages
        .where((m) => alphanumericRegex.hasMatch(m.sender))
        .toList();

    if (bankMessages.isEmpty) {
      return SyncResult(received: 0, created: 0, queued: 0, ignored: 0, duplicates: 0);
    }

    // Track newest message date
    DateTime newestDate = since;
    for (final m in bankMessages) {
      if (m.date.isAfter(newestDate)) {
        newestDate = m.date;
      }
    }

    // 5. Send in chunks of at most 100 to POST /sms/ingest
    SyncResult totalResult = SyncResult(received: 0, created: 0, queued: 0, ignored: 0, duplicates: 0);
    const int chunkSize = 100;

    for (int i = 0; i < bankMessages.length; i += chunkSize) {
      final chunk = bankMessages.sublist(
        i,
        i + chunkSize > bankMessages.length ? bankMessages.length : i + chunkSize,
      );

      final payload = chunk.map((m) => {
        'sender': m.sender,
        'rawText': m.body,
        'receivedAt': m.date.toUtc().toIso8601String(),
      }).toList();

      final responseJson = await _expenseRepository.ingestSms(payload);
      final chunkResult = SyncResult(
        received: responseJson['received'] as int? ?? 0,
        created: responseJson['created'] as int? ?? 0,
        queued: responseJson['queued'] as int? ?? 0,
        ignored: responseJson['ignored'] as int? ?? 0,
        duplicates: responseJson['duplicates'] as int? ?? 0,
      );
      totalResult = totalResult + chunkResult;
    }

    // 6. After ALL chunks succeed, store the newest message date
    await prefs.setInt('sms_last_sync_ms', newestDate.millisecondsSinceEpoch);

    // B3: Check budget alerts after successful SMS sync
    await BudgetAlertService.checkBudgetsAndNotify(_expenseRepository);

    return totalResult;
  }
}

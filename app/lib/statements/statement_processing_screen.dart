import 'dart:async';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../auth/auth_service.dart';
import 'models.dart';
import 'statement_repository.dart';
import 'statement_review_screen.dart';

class StatementProcessingScreen extends StatefulWidget {
  final String importId;

  const StatementProcessingScreen({super.key, required this.importId});

  @override
  State<StatementProcessingScreen> createState() => _StatementProcessingScreenState();
}

class _StatementProcessingScreenState extends State<StatementProcessingScreen> {
  late final StatementRepository _repository;
  Timer? _pollingTimer;
  int _pollCount = 0;
  static const int _maxPolls = 200; // 200 * 3s = 600s = 10 minutes

  bool _isTimedOut = false;
  String? _failedErrorMessage;

  @override
  void initState() {
    super.initState();
    final apiClient = context.read<AuthService>().apiClient;
    _repository = StatementRepository(apiClient);
    _startPolling();
  }

  @override
  void dispose() {
    _pollingTimer?.cancel();
    super.dispose();
  }

  void _startPolling() {
    _checkStatus();
    _pollingTimer = Timer.periodic(const Duration(seconds: 3), (timer) {
      _pollCount++;
      if (_pollCount >= _maxPolls) {
        timer.cancel();
        if (mounted) {
          setState(() {
            _isTimedOut = true;
          });
        }
        return;
      }
      _checkStatus();
    });
  }

  Future<void> _checkStatus() async {
    try {
      final imp = await _repository.getImport(widget.importId);
      if (!mounted) return;

      if (imp.status == 'ready') {
        _pollingTimer?.cancel();
        // Replace with Review screen
        final res = await Navigator.pushReplacement<bool, void>(
          context,
          MaterialPageRoute(
            builder: (_) => StatementReviewScreen(importId: widget.importId),
          ),
        );
        if (mounted) {
          Navigator.pop(context, res);
        }
      } else if (imp.status == 'failed') {
        _pollingTimer?.cancel();
        setState(() {
          _failedErrorMessage = _mapErrorCodeToMessage(imp.errorCode);
        });
      }
    } catch (_) {
      // Continue polling on transient network glitches
    }
  }

  String _mapErrorCodeToMessage(String? errorCode) {
    switch (errorCode) {
      case 'PASSWORD_REQUIRED':
        return 'This PDF needs a password. Go back and enter it.';
      case 'WRONG_PASSWORD':
        return 'The password is wrong. Go back and try again.';
      case 'UNREADABLE_FILE':
        return 'This file could not be read.';
      case 'TOO_MANY_PAGES':
        return 'This statement has too many pages (max 10). Split it and import in parts.';
      case 'NO_PARSER':
        return 'Statements from this bank are not supported yet.';
      case 'NO_ROWS_FOUND':
        return 'No transactions were found in this file.';
      case 'OCR_UNAVAILABLE':
        return 'Reading scanned files is not available right now.';
      case 'OCR_TIMEOUT':
        return 'Reading this file took too long. Try a PDF from net banking.';
      case 'INTERNAL_ERROR':
      default:
        return 'Something went wrong. Please try again.';
    }
  }

  @override
  Widget build(BuildContext context) {
    return WillPopScope(
      onWillPop: () async {
        _pollingTimer?.cancel();
        return true;
      },
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Processing Statement'),
          automaticallyImplyLeading: _failedErrorMessage != null || _isTimedOut,
        ),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: _buildContent(),
          ),
        ),
      ),
    );
  }

  Widget _buildContent() {
    if (_isTimedOut) {
      return Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.timer_off_outlined, size: 64, color: Colors.amber),
          const SizedBox(height: 16),
          const Text(
            'Taking too long',
            style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 8),
          const Text(
            'Reading this file is taking longer than expected. You can check back later in Past Imports.',
            textAlign: TextAlign.center,
            style: TextStyle(color: Colors.white70),
          ),
          const SizedBox(height: 24),
          OutlinedButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Back'),
          ),
        ],
      );
    }

    if (_failedErrorMessage != null) {
      return Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.error_outline, size: 64, color: Colors.redAccent),
          const SizedBox(height: 16),
          const Text(
            'Processing Failed',
            style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 8),
          Text(
            _failedErrorMessage!,
            textAlign: TextAlign.center,
            style: const TextStyle(color: Colors.white70, fontSize: 15),
          ),
          const SizedBox(height: 24),
          FilledButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Back'),
          ),
        ],
      );
    }

    return const Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        CircularProgressIndicator(),
        SizedBox(height: 24),
        Text(
          'Reading your statement...',
          style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
        ),
        SizedBox(height: 8),
        Text(
          'This can take a minute. OCR runs locally on the server.',
          textAlign: TextAlign.center,
          style: TextStyle(color: Colors.white70, fontSize: 14),
        ),
      ],
    );
  }
}

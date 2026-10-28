import 'dart:async';
import 'package:flutter/material.dart';

class LoadingStateWidget extends StatefulWidget {
  final String? initialMessage;

  const LoadingStateWidget({
    super.key,
    this.initialMessage,
  });

  @override
  State<LoadingStateWidget> createState() => _LoadingStateWidgetState();
}

class _LoadingStateWidgetState extends State<LoadingStateWidget> {
  bool _showColdStartHint = false;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    // After 8 seconds of waiting show cold start hint
    _timer = Timer(const Duration(seconds: 8), () {
      if (mounted) {
        setState(() {
          _showColdStartHint = true;
        });
      }
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          mainAxisSize: MainAxisSize.min,
          children: [
            const CircularProgressIndicator(),
            const SizedBox(height: 16),
            if (widget.initialMessage != null && !_showColdStartHint)
              Text(
                widget.initialMessage!,
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodyMedium,
              ),
            if (_showColdStartHint) ...[
              const SizedBox(height: 8),
              Text(
                'The server is waking up, this can take up to a minute.',
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: Theme.of(context).colorScheme.outline,
                    ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class ErrorStateWidget extends StatelessWidget {
  final String? message;
  final VoidCallback onRetry;

  const ErrorStateWidget({
    super.key,
    this.message,
    required this.onRetry,
  });

  @override
  Widget build(BuildContext context) {
    final displayMessage = _formatMessage(message);

    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              Icons.cloud_off_outlined,
              size: 48,
              color: Theme.of(context).colorScheme.error,
            ),
            const SizedBox(height: 16),
            Text(
              displayMessage,
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.bodyMedium,
            ),
            const SizedBox(height: 16),
            FilledButton.tonal(
              onPressed: onRetry,
              child: const Text('Retry'),
            ),
          ],
        ),
      ),
    );
  }

  static String _formatMessage(String? error) {
    if (error == null || error.trim().isEmpty) {
      return 'Something went wrong. Please try again.';
    }
    final lower = error.toLowerCase();
    if (lower.contains('socketexception') ||
        lower.contains('network') ||
        lower.contains('connection refused') ||
        lower.contains('failed host lookup') ||
        lower.contains('clientexception')) {
      return 'No connection. Showing no new data.';
    }
    return error;
  }
}

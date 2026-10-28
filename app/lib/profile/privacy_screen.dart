import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../auth/auth_service.dart';
import '../auth/login_screen.dart';
import '../core/file_exporter.dart';

class PrivacyScreen extends StatefulWidget {
  const PrivacyScreen({super.key});

  @override
  State<PrivacyScreen> createState() => _PrivacyScreenState();
}

class _PrivacyScreenState extends State<PrivacyScreen> {
  bool _isExporting = false;
  bool _isDeleting = false;

  Future<void> _exportData() async {
    setState(() => _isExporting = true);
    final authService = context.read<AuthService>();

    try {
      final response = await authService.apiClient.get('/api/v1/export');
      if (response.statusCode == 200) {
        await FileExporter.exportJson(response.body, 'life-tracker-export.json');
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Data export ready to share/save')),
          );
        }
      } else {
        throw Exception('Export failed (${response.statusCode})');
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to export data: ${e.toString().replaceFirst("Exception: ", "")}')),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _isExporting = false);
      }
    }
  }

  Future<void> _confirmDeleteAccount() async {
    final confirmationController = TextEditingController();
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) {
        return StatefulBuilder(
          builder: (dialogCtx, setDialogState) {
            final isMatch = confirmationController.text.trim() == 'DELETE';

            return AlertDialog(
              title: const Text('Delete Account'),
              content: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'This action is irreversible. All your transactions, budgets, fitness data, mood logs, and settings will be permanently erased.',
                  ),
                  const SizedBox(height: 16),
                  const Text(
                    'To confirm, type DELETE below:',
                    style: TextStyle(fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(height: 8),
                  TextField(
                    controller: confirmationController,
                    autofocus: true,
                    decoration: const InputDecoration(
                      hintText: 'DELETE',
                      border: OutlineInputBorder(),
                    ),
                    onChanged: (_) => setDialogState(() {}),
                  ),
                ],
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.pop(ctx, false),
                  child: const Text('Cancel'),
                ),
                FilledButton(
                  style: FilledButton.styleFrom(backgroundColor: Colors.redAccent),
                  onPressed: isMatch ? () => Navigator.pop(ctx, true) : null,
                  child: const Text('Permanently Delete'),
                ),
              ],
            );
          },
        );
      },
    );

    confirmationController.dispose();

    if (confirmed == true && mounted) {
      setState(() => _isDeleting = true);
      final authService = context.read<AuthService>();
      try {
        final res = await authService.apiClient.delete('/api/v1/account');
        if (res.statusCode == 204 || res.statusCode == 200) {
          await authService.logout();
          if (mounted) {
            Navigator.pushAndRemoveUntil(
              context,
              MaterialPageRoute(builder: (_) => const LoginScreen()),
              (route) => false,
            );
          }
        } else {
          throw Exception('Failed to delete account (${res.statusCode})');
        }
      } catch (e) {
        if (mounted) {
          setState(() => _isDeleting = false);
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Delete failed: ${e.toString().replaceFirst("Exception: ", "")}')),
          );
        }
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Privacy & Data'),
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
        children: [
          Card(
            elevation: 0,
            color: Theme.of(context).colorScheme.surfaceVariant.withOpacity(0.4),
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
            child: const Padding(
              padding: EdgeInsets.all(16.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(Icons.shield_outlined, color: Colors.green),
                      SizedBox(width: 8),
                      Text(
                        'Your Privacy Guarantee',
                        style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                      ),
                    ],
                  ),
                  SizedBox(height: 12),
                  Text(
                    'Your data is stored in your own free database. SMS from unknown senders is ignored. Only bank-style senders are read, and the message text is deleted after parsing. Uploaded statements are deleted after processing. AI summaries send only computed numbers (see AI settings).',
                    style: TextStyle(height: 1.4),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 24),
          const Text(
            'Data Portability',
            style: TextStyle(fontSize: 15, fontWeight: FontWeight.w600),
          ),
          const SizedBox(height: 8),
          const Text(
            'You have full ownership of your data. You can download an export of all your transactions, budgets, fitness, and mood history as JSON at any time.',
            style: TextStyle(color: Colors.grey, fontSize: 13),
          ),
          const SizedBox(height: 12),
          FilledButton.tonalIcon(
            onPressed: _isExporting ? null : _exportData,
            icon: _isExporting
                ? const SizedBox(
                    width: 16,
                    height: 16,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.download_outlined),
            label: const Text('Export my data'),
          ),
          const SizedBox(height: 32),
          const Divider(),
          const SizedBox(height: 16),
          const Text(
            'Account Removal',
            style: TextStyle(fontSize: 15, fontWeight: FontWeight.w600),
          ),
          const SizedBox(height: 8),
          const Text(
            'Permanently delete your account and all associated personal data from the database.',
            style: TextStyle(color: Colors.grey, fontSize: 13),
          ),
          const SizedBox(height: 12),
          OutlinedButton.icon(
            onPressed: _isDeleting ? null : _confirmDeleteAccount,
            icon: const Icon(Icons.delete_forever_outlined, color: Colors.redAccent),
            label: const Text(
              'Delete my account',
              style: TextStyle(color: Colors.redAccent),
            ),
            style: OutlinedButton.styleFrom(
              side: const BorderSide(color: Colors.redAccent),
            ),
          ),
        ],
      ),
    );
  }
}

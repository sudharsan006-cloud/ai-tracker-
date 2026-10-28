import 'dart:typed_data';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import '../auth/auth_service.dart';
import '../core/widgets/empty_state_widget.dart';
import '../expenses/expense_repository.dart';
import 'models.dart';
import 'statement_processing_screen.dart';
import 'statement_repository.dart';
import 'statement_review_screen.dart';

class StatementImportScreen extends StatefulWidget {
  const StatementImportScreen({super.key});

  @override
  State<StatementImportScreen> createState() => _StatementImportScreenState();
}

class _StatementImportScreenState extends State<StatementImportScreen> {
  late final ExpenseRepository _expenseRepository;
  late final StatementRepository _statementRepository;

  List<Map<String, dynamic>> _bankAccounts = [];
  List<StatementImport> _pastImports = [];
  bool _isLoadingData = true;
  bool _isUploading = false;
  String? _errorMessage;

  String? _selectedAccountId;
  String? _chosenFileName;
  int? _chosenFileSize;
  Uint8List? _chosenFileBytes;

  final TextEditingController _passwordController = TextEditingController();
  bool _obscurePassword = true;

  @override
  void initState() {
    super.initState();
    final apiClient = context.read<AuthService>().apiClient;
    _expenseRepository = ExpenseRepository(apiClient);
    _statementRepository = StatementRepository(apiClient);
    _loadInitialData();
  }

  @override
  void dispose() {
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _loadInitialData() async {
    setState(() {
      _isLoadingData = true;
      _errorMessage = null;
    });

    try {
      final accounts = await _expenseRepository.getBankAccounts();
      final past = await _statementRepository.listImports();

      if (mounted) {
        setState(() {
          _bankAccounts = accounts.where((a) => a['isActive'] == true).toList();
          _pastImports = past;
          if (_selectedAccountId == null && _bankAccounts.isNotEmpty) {
            _selectedAccountId = _bankAccounts.first['id'] as String;
          }
          _isLoadingData = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _errorMessage = e.toString().replaceFirst('Exception: ', '');
          _isLoadingData = false;
        });
      }
    }
  }

  Future<void> _pickFile() async {
    try {
      final result = await FilePicker.platform.pickFiles(
        type: FileType.custom,
        allowedExtensions: ['pdf', 'png', 'jpg', 'jpeg'],
        withData: true,
      );

      if (result != null && result.files.isNotEmpty) {
        final file = result.files.first;
        if (file.bytes == null) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Could not read file data. Please try again.')),
          );
          return;
        }

        setState(() {
          _chosenFileName = file.name;
          _chosenFileSize = file.size;
          _chosenFileBytes = file.bytes;
          _errorMessage = null;
        });
      }
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Failed to pick file: $e')),
      );
    }
  }

  String _formatFileSize(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    return '${(bytes / (1024 * 1024)).toStringAsFixed(2)} MB';
  }

  Future<void> _handleUpload() async {
    // Client-side validations
    if (_selectedAccountId == null || _selectedAccountId!.isEmpty) {
      setState(() => _errorMessage = 'Please select a bank account');
      return;
    }
    if (_chosenFileBytes == null || _chosenFileName == null) {
      setState(() => _errorMessage = 'Please choose a statement file to upload');
      return;
    }
    if (_chosenFileSize != null && _chosenFileSize! > 5 * 1024 * 1024) {
      setState(() => _errorMessage = 'File is too large (max 5 MB)');
      return;
    }

    setState(() {
      _isUploading = true;
      _errorMessage = null;
    });

    try {
      final imp = await _statementRepository.uploadStatement(
        bankAccountId: _selectedAccountId!,
        fileName: _chosenFileName!,
        fileBytes: _chosenFileBytes!,
        password: _passwordController.text.trim().isNotEmpty ? _passwordController.text.trim() : null,
      );

      if (mounted) {
        setState(() => _isUploading = false);
        // Navigate to processing screen
        final res = await Navigator.push<bool>(
          context,
          MaterialPageRoute(
            builder: (_) => StatementProcessingScreen(importId: imp.id),
          ),
        );
        if (res == true) {
          Navigator.pop(context, true);
        } else {
          _loadInitialData();
        }
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isUploading = false;
          _errorMessage = e.toString().replaceFirst('Exception: ', '');
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final dateFormat = DateFormat('yyyy-MM-dd HH:mm');

    return Scaffold(
      appBar: AppBar(
        title: const Text('Import Bank Statement'),
      ),
      body: _isLoadingData
          ? const Center(child: CircularProgressIndicator())
          : SingleChildScrollView(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  // Bank Account Dropdown
                  const Text(
                    'Bank Account',
                    style: TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
                  ),
                  const SizedBox(height: 8),
                  DropdownButtonFormField<String>(
                    value: _selectedAccountId,
                    decoration: const InputDecoration(
                      border: OutlineInputBorder(),
                      contentPadding: EdgeInsets.symmetric(horizontal: 12, vertical: 12),
                    ),
                    items: _bankAccounts.map((a) {
                      final id = a['id'] as String;
                      final nickname = a['nickname'] as String? ?? 'Account';
                      final last4 = a['accountLast4'] as String? ?? '';
                      return DropdownMenuItem<String>(
                        value: id,
                        child: Text('$nickname (..$last4)'),
                      );
                    }).toList(),
                    onChanged: (val) {
                      setState(() => _selectedAccountId = val);
                    },
                  ),
                  const SizedBox(height: 20),

                  // Choose File Button and details
                  const Text(
                    'Statement File (PDF, PNG or JPG, max 5 MB)',
                    style: TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
                  ),
                  const SizedBox(height: 8),
                  OutlinedButton.icon(
                    onPressed: _isUploading ? null : _pickFile,
                    icon: const Icon(Icons.attach_file),
                    label: Text(_chosenFileName != null ? 'Change File' : 'Choose file'),
                    style: OutlinedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(vertical: 14),
                    ),
                  ),
                  if (_chosenFileName != null) ...[
                    const SizedBox(height: 8),
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: Colors.white.withOpacity(0.05),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Row(
                        children: [
                          const Icon(Icons.description, size: 20, color: Colors.blueAccent),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              _chosenFileName!,
                              style: const TextStyle(fontWeight: FontWeight.w500),
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                          if (_chosenFileSize != null)
                            Text(
                              _formatFileSize(_chosenFileSize!),
                              style: const TextStyle(color: Colors.white70, fontSize: 12),
                            ),
                        ],
                      ),
                    ),
                  ],
                  const SizedBox(height: 20),

                  // Password field
                  TextField(
                    controller: _passwordController,
                    obscureText: _obscurePassword,
                    decoration: InputDecoration(
                      labelText: 'PDF password (leave empty if none)',
                      border: const OutlineInputBorder(),
                      suffixIcon: IconButton(
                        icon: Icon(_obscurePassword ? Icons.visibility_off : Icons.visibility),
                        onPressed: () {
                          setState(() => _obscurePassword = !_obscurePassword);
                        },
                      ),
                    ),
                  ),
                  const SizedBox(height: 12),

                  // Hint text
                  Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: Colors.amber.withOpacity(0.1),
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(color: Colors.amber.withOpacity(0.3)),
                    ),
                    child: const Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Icon(Icons.info_outline, size: 18, color: Colors.amber),
                        SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            "For best results download the PDF statement from your bank's net banking. Photos and scans are slower and less accurate.",
                            style: TextStyle(fontSize: 13, color: Colors.white70),
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 20),

                  // Error Banner
                  if (_errorMessage != null) ...[
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: Colors.red.withOpacity(0.15),
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(color: Colors.red.withOpacity(0.5)),
                      ),
                      child: Row(
                        children: [
                          const Icon(Icons.error_outline, size: 20, color: Colors.redAccent),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              _errorMessage!,
                              style: const TextStyle(color: Colors.redAccent, fontSize: 13),
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 16),
                  ],

                  // Upload Button
                  FilledButton(
                    onPressed: _isUploading ? null : _handleUpload,
                    style: FilledButton.styleFrom(
                      padding: const EdgeInsets.symmetric(vertical: 16),
                    ),
                    child: _isUploading
                        ? const SizedBox(
                            width: 20,
                            height: 20,
                            child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                          )
                        : const Text('Upload Statement', style: TextStyle(fontSize: 16)),
                  ),
                  const SizedBox(height: 32),

                  // Past Imports section
                  const Divider(),
                  const SizedBox(height: 12),
                  const Text(
                    'Past Imports',
                    style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                  ),
                  const SizedBox(height: 8),
                  if (_pastImports.isEmpty)
                    const EmptyStateWidget(
                      icon: Icons.history_outlined,
                      message: 'No previous statement imports found.',
                    )
                  else
                    ListView.separated(
                      shrinkWrap: true,
                      physics: const NeverScrollableScrollPhysics(),
                      itemCount: _pastImports.length,
                      separatorBuilder: (_, __) => const Divider(height: 1),
                      itemBuilder: (context, index) {
                        final imp = _pastImports[index];
                        final dateStr = imp.createdAt != null
                            ? dateFormat.format(imp.createdAt!.toLocal())
                            : '';
                        Color badgeColor = Colors.grey;
                        if (imp.status == 'ready') badgeColor = Colors.green;
                        if (imp.status == 'processing') badgeColor = Colors.blue;
                        if (imp.status == 'failed') badgeColor = Colors.red;
                        if (imp.status == 'committed') badgeColor = Colors.teal;

                        return ListTile(
                          contentPadding: EdgeInsets.zero,
                          title: Text(
                            '${imp.fileType.toUpperCase()} Statement ($dateStr)',
                            style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w500),
                          ),
                          subtitle: Text(
                            imp.status == 'ready'
                                ? '${imp.rowCount ?? 0} rows found • Tap to review'
                                : (imp.status == 'failed'
                                    ? 'Failed: ${imp.errorCode ?? "Error"}'
                                    : 'Status: ${imp.status}'),
                            style: const TextStyle(fontSize: 12),
                          ),
                          trailing: Container(
                            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                            decoration: BoxDecoration(
                              color: badgeColor.withOpacity(0.15),
                              borderRadius: BorderRadius.circular(4),
                              border: Border.all(color: badgeColor.withOpacity(0.5)),
                            ),
                            child: Text(
                              imp.status.toUpperCase(),
                              style: TextStyle(color: badgeColor, fontSize: 11, fontWeight: FontWeight.bold),
                            ),
                          ),
                          onTap: () async {
                            if (imp.status == 'ready') {
                              final res = await Navigator.push<bool>(
                                context,
                                MaterialPageRoute(
                                  builder: (_) => StatementReviewScreen(importId: imp.id),
                                ),
                              );
                              if (res == true) {
                                Navigator.pop(context, true);
                              } else {
                                _loadInitialData();
                              }
                            } else if (imp.status == 'processing') {
                              final res = await Navigator.push<bool>(
                                context,
                                MaterialPageRoute(
                                  builder: (_) => StatementProcessingScreen(importId: imp.id),
                                ),
                              );
                              if (res == true) {
                                Navigator.pop(context, true);
                              } else {
                                _loadInitialData();
                              }
                            }
                          },
                        );
                      },
                    ),
                ],
              ),
            ),
    );
  }
}

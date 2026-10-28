import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../auth/auth_service.dart';

class BankAccountsScreen extends StatefulWidget {
  const BankAccountsScreen({super.key});

  @override
  State<BankAccountsScreen> createState() => _BankAccountsScreenState();
}

class _BankAccountsScreenState extends State<BankAccountsScreen> {
  bool _isLoading = true;
  String? _errorMessage;

  List<Map<String, dynamic>> _accounts = [];
  List<Map<String, dynamic>> _banks = [];
  List<Map<String, dynamic>> _currencies = [];

  @override
  void initState() {
    super.initState();
    _loadAll();
  }

  Future<void> _loadAll() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    final authService = context.read<AuthService>();

    try {
      final futures = await Future.wait([
        authService.apiClient.get('/api/v1/bank-accounts'),
        authService.apiClient.get('/api/v1/banks'),
        authService.apiClient.get('/api/v1/currencies'),
      ]);

      final accountsRes = futures[0];
      final banksRes = futures[1];
      final currenciesRes = futures[2];

      if (accountsRes.statusCode == 200 &&
          banksRes.statusCode == 200 &&
          currenciesRes.statusCode == 200) {
        setState(() {
          _accounts = List<Map<String, dynamic>>.from(jsonDecode(accountsRes.body));
          _banks = List<Map<String, dynamic>>.from(jsonDecode(banksRes.body));
          _currencies = List<Map<String, dynamic>>.from(jsonDecode(currenciesRes.body));
          _isLoading = false;
        });
      } else {
        setState(() {
          _errorMessage = 'Failed to load bank data';
          _isLoading = false;
        });
      }
    } catch (_) {
      setState(() {
        _errorMessage = 'Network error loading bank accounts';
        _isLoading = false;
      });
    }
  }

  String _getBankName(int bankId) {
    final match = _banks.firstWhere((b) => b['id'] == bankId, orElse: () => {});
    return match['name'] ?? 'Bank #$bankId';
  }

  String _getCurrencyCode(int currencyId) {
    final match = _currencies.firstWhere((c) => c['id'] == currencyId, orElse: () => {});
    return match['code'] ?? 'INR';
  }

  Future<void> _openCreateDialog() async {
    int? selectedBankId = _banks.isNotEmpty ? _banks.first['id'] as int : null;
    int? selectedCurrencyId = _currencies.isNotEmpty ? _currencies.first['id'] as int : null;
    final last4Controller = TextEditingController();
    final nicknameController = TextEditingController();
    String selectedType = 'savings';

    final formKey = GlobalKey<FormState>();
    String? dialogError;

    await showDialog(
      context: context,
      builder: (ctx) {
        return StatefulBuilder(
          builder: (context, setDialogState) {
            return AlertDialog(
              title: const Text('Add Bank Account'),
              content: SingleChildScrollView(
                child: Form(
                  key: formKey,
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      if (dialogError != null) ...[
                        Container(
                          padding: const EdgeInsets.all(8),
                          margin: const EdgeInsets.only(bottom: 12),
                          decoration: BoxDecoration(
                            color: Theme.of(context).colorScheme.errorContainer,
                            borderRadius: BorderRadius.circular(6),
                          ),
                          child: Text(
                            dialogError!,
                            style: TextStyle(
                              color: Theme.of(context).colorScheme.onErrorContainer,
                              fontSize: 13,
                            ),
                          ),
                        ),
                      ],
                      DropdownButtonFormField<int>(
                        value: selectedBankId,
                        decoration: const InputDecoration(labelText: 'Bank', border: OutlineInputBorder()),
                        items: _banks.map((b) {
                          return DropdownMenuItem<int>(
                            value: b['id'] as int,
                            child: Text(b['name'] as String),
                          );
                        }).toList(),
                        onChanged: (val) => setDialogState(() => selectedBankId = val),
                        validator: (val) => val == null ? 'Bank is required' : null,
                      ),
                      const SizedBox(height: 12),
                      DropdownButtonFormField<int>(
                        value: selectedCurrencyId,
                        decoration: const InputDecoration(labelText: 'Currency', border: OutlineInputBorder()),
                        items: _currencies.map((c) {
                          return DropdownMenuItem<int>(
                            value: c['id'] as int,
                            child: Text('${c['code']} (${c['symbol']})'),
                          );
                        }).toList(),
                        onChanged: (val) => setDialogState(() => selectedCurrencyId = val),
                        validator: (val) => val == null ? 'Currency is required' : null,
                      ),
                      const SizedBox(height: 12),
                      TextFormField(
                        controller: nicknameController,
                        decoration: const InputDecoration(labelText: 'Nickname (optional)', border: OutlineInputBorder()),
                      ),
                      const SizedBox(height: 12),
                      TextFormField(
                        controller: last4Controller,
                        keyboardType: TextInputType.number,
                        maxLength: 4,
                        decoration: const InputDecoration(
                          labelText: 'Last 4 digits (optional)',
                          border: OutlineInputBorder(),
                          counterText: '',
                        ),
                        validator: (val) {
                          if (val != null && val.isNotEmpty && !RegExp(r'^[0-9]{4}$').hasMatch(val)) {
                            return 'Must be exactly 4 digits';
                          }
                          return null;
                        },
                      ),
                      const SizedBox(height: 12),
                      DropdownButtonFormField<String>(
                        value: selectedType,
                        decoration: const InputDecoration(labelText: 'Account Type', border: OutlineInputBorder()),
                        items: const [
                          DropdownMenuItem(value: 'savings', child: Text('Savings')),
                          DropdownMenuItem(value: 'current', child: Text('Current')),
                          DropdownMenuItem(value: 'credit', child: Text('Credit')),
                        ],
                        onChanged: (val) => setDialogState(() => selectedType = val ?? 'savings'),
                      ),
                    ],
                  ),
                ),
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.pop(ctx),
                  child: const Text('Cancel'),
                ),
                ElevatedButton(
                  onPressed: () async {
                    if (!formKey.currentState!.validate()) return;
                    final authService = this.context.read<AuthService>();
                    final body = {
                      'bankId': selectedBankId,
                      'currencyId': selectedCurrencyId,
                      if (nicknameController.text.trim().isNotEmpty)
                        'nickname': nicknameController.text.trim(),
                      if (last4Controller.text.trim().isNotEmpty)
                        'accountLast4': last4Controller.text.trim(),
                      'accountType': selectedType,
                    };

                    try {
                      final res = await authService.apiClient.post('/api/v1/bank-accounts', body: body);
                      if (res.statusCode == 201) {
                        if (mounted) Navigator.pop(ctx);
                        _loadAll();
                      } else {
                        final errorData = jsonDecode(res.body);
                        setDialogState(() {
                          dialogError = errorData['message'] ?? 'Failed to add account (${res.statusCode})';
                        });
                      }
                    } catch (_) {
                      setDialogState(() {
                        dialogError = 'Network error while adding account';
                      });
                    }
                  },
                  child: const Text('Add Account'),
                ),
              ],
            );
          },
        );
      },
    );
  }

  Future<void> _openEditDialog(Map<String, dynamic> account) async {
    final nicknameController = TextEditingController(text: account['nickname'] ?? '');
    final last4Controller = TextEditingController(text: account['accountLast4'] ?? '');
    String selectedType = account['accountType'] ?? 'savings';
    final formKey = GlobalKey<FormState>();

    await showDialog(
      context: context,
      builder: (ctx) {
        return StatefulBuilder(
          builder: (context, setDialogState) {
            return AlertDialog(
              title: const Text('Edit Bank Account'),
              content: Form(
                key: formKey,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    TextFormField(
                      controller: nicknameController,
                      decoration: const InputDecoration(labelText: 'Nickname', border: OutlineInputBorder()),
                    ),
                    const SizedBox(height: 12),
                    TextFormField(
                      controller: last4Controller,
                      keyboardType: TextInputType.number,
                      maxLength: 4,
                      decoration: const InputDecoration(
                        labelText: 'Last 4 digits',
                        border: OutlineInputBorder(),
                        counterText: '',
                      ),
                      validator: (val) {
                        if (val != null && val.isNotEmpty && !RegExp(r'^[0-9]{4}$').hasMatch(val)) {
                          return 'Must be exactly 4 digits';
                        }
                        return null;
                      },
                    ),
                    const SizedBox(height: 12),
                    DropdownButtonFormField<String>(
                      value: selectedType,
                      decoration: const InputDecoration(labelText: 'Account Type', border: OutlineInputBorder()),
                      items: const [
                        DropdownMenuItem(value: 'savings', child: Text('Savings')),
                        DropdownMenuItem(value: 'current', child: Text('Current')),
                        DropdownMenuItem(value: 'credit', child: Text('Credit')),
                      ],
                      onChanged: (val) => setDialogState(() => selectedType = val ?? 'savings'),
                    ),
                  ],
                ),
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.pop(ctx),
                  child: const Text('Cancel'),
                ),
                ElevatedButton(
                  onPressed: () async {
                    if (!formKey.currentState!.validate()) return;
                    final authService = this.context.read<AuthService>();
                    final body = {
                      'nickname': nicknameController.text.trim().isEmpty ? null : nicknameController.text.trim(),
                      'accountLast4': last4Controller.text.trim().isEmpty ? null : last4Controller.text.trim(),
                      'accountType': selectedType,
                    };

                    try {
                      final res = await authService.apiClient.patch(
                        '/api/v1/bank-accounts/${account['id']}',
                        body: body,
                      );
                      if (res.statusCode == 200) {
                        if (mounted) Navigator.pop(ctx);
                        _loadAll();
                      }
                    } catch (_) {}
                  },
                  child: const Text('Save'),
                ),
              ],
            );
          },
        );
      },
    );
  }

  Future<void> _confirmDelete(Map<String, dynamic> account) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Deactivate Account?'),
        content: Text(
          'Are you sure you want to deactivate ${account['nickname'] ?? _getBankName(account['bankId'])}?',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: Theme.of(ctx).colorScheme.error,
              foregroundColor: Theme.of(ctx).colorScheme.onError,
            ),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Deactivate'),
          ),
        ],
      ),
    );

    if (confirmed == true && mounted) {
      final authService = context.read<AuthService>();
      try {
        final res = await authService.apiClient.delete('/api/v1/bank-accounts/${account['id']}');
        if (res.statusCode == 204) {
          _loadAll();
        }
      } catch (_) {}
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Bank Accounts'),
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: _openCreateDialog,
        tooltip: 'Add Bank Account',
        child: const Icon(Icons.add),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : _errorMessage != null
              ? Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Text(_errorMessage!, style: const TextStyle(color: Colors.redAccent)),
                      const SizedBox(height: 12),
                      ElevatedButton(onPressed: _loadAll, child: const Text('Retry')),
                    ],
                  ),
                )
              : _accounts.isEmpty
                  ? Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          const Icon(Icons.account_balance_outlined, size: 64, color: Colors.white38),
                          const SizedBox(height: 16),
                          const Text('No bank accounts yet', style: TextStyle(fontSize: 18, color: Colors.white60)),
                          const SizedBox(height: 8),
                          ElevatedButton.icon(
                            onPressed: _openCreateDialog,
                            icon: const Icon(Icons.add),
                            label: const Text('Add Account'),
                          ),
                        ],
                      ),
                    )
                  : RefreshIndicator(
                      onRefresh: _loadAll,
                      child: ListView.builder(
                        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                        itemCount: _accounts.length,
                        itemBuilder: (context, index) {
                          final acc = _accounts[index];
                          final bankName = _getBankName(acc['bankId']);
                          final currencyCode = _getCurrencyCode(acc['currencyId']);
                          final nickname = acc['nickname'] as String?;
                          final last4 = acc['accountLast4'] as String?;
                          final type = (acc['accountType'] as String? ?? 'savings').toUpperCase();

                          final title = (nickname != null && nickname.isNotEmpty)
                              ? nickname
                              : '$bankName ${last4 != null ? '••$last4' : ''}';

                          final subtitle = '$bankName · $currencyCode · $type';

                          return Card(
                            margin: const EdgeInsets.only(bottom: 12),
                            child: ListTile(
                              leading: CircleAvatar(
                                backgroundColor: const Color(0xFF6366F1).withOpacity(0.2),
                                child: const Icon(Icons.account_balance, color: Color(0xFF818CF8)),
                              ),
                              title: Text(title, style: const TextStyle(fontWeight: FontWeight.bold)),
                              subtitle: Text(subtitle),
                              trailing: Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  IconButton(
                                    icon: const Icon(Icons.edit_outlined),
                                    tooltip: 'Edit',
                                    onPressed: () => _openEditDialog(acc),
                                  ),
                                  IconButton(
                                    icon: const Icon(Icons.delete_outline, color: Colors.redAccent),
                                    tooltip: 'Deactivate',
                                    onPressed: () => _confirmDelete(acc),
                                  ),
                                ],
                              ),
                            ),
                          );
                        },
                      ),
                    ),
    );
  }
}

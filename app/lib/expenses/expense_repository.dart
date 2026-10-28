import 'dart:convert';
import 'package:http/http.dart' as http;
import '../core/api_client.dart';
import 'models.dart';

class ExpenseRepository {
  final ApiClient apiClient;

  ExpenseRepository(this.apiClient);

  String _extractErrorMessage(http.Response response) {
    try {
      final data = jsonDecode(response.body);
      if (data is Map<String, dynamic> && data['message'] != null) {
        return data['message'] as String;
      }
    } catch (_) {}
    return 'Request failed with status ${response.statusCode}';
  }

  // Categories
  Future<List<Category>> getCategories() async {
    final res = await apiClient.get('/api/v1/categories');
    if (res.statusCode == 200) {
      final list = jsonDecode(res.body) as List<dynamic>;
      return list.map((e) => Category.fromJson(e as Map<String, dynamic>)).toList();
    }
    throw Exception(_extractErrorMessage(res));
  }

  Future<Category> createCategory(String name, String type, String? icon) async {
    final body = <String, dynamic>{
      'name': name,
      'type': type,
      if (icon != null) 'icon': icon,
    };
    final res = await apiClient.post('/api/v1/categories', body: body);
    if (res.statusCode == 201 || res.statusCode == 200) {
      return Category.fromJson(jsonDecode(res.body) as Map<String, dynamic>);
    }
    throw Exception(_extractErrorMessage(res));
  }

  Future<Category> patchCategory(String id, {String? name, String? icon}) async {
    final body = <String, dynamic>{
      if (name != null) 'name': name,
      if (icon != null) 'icon': icon,
    };
    final res = await apiClient.patch('/api/v1/categories/$id', body: body);
    if (res.statusCode == 200) {
      return Category.fromJson(jsonDecode(res.body) as Map<String, dynamic>);
    }
    throw Exception(_extractErrorMessage(res));
  }

  Future<void> deleteCategory(String id) async {
    final res = await apiClient.delete('/api/v1/categories/$id');
    if (res.statusCode != 204 && res.statusCode != 200) {
      throw Exception(_extractErrorMessage(res));
    }
  }

  // Transactions
  Future<TransactionPage> getTransactions({
    String? from,
    String? to,
    String? categoryId,
    String? bankAccountId,
    String? direction,
    int page = 0,
    int size = 50,
  }) async {
    final params = <String>[];
    if (from != null && from.isNotEmpty) params.add('from=$from');
    if (to != null && to.isNotEmpty) params.add('to=$to');
    if (categoryId != null && categoryId.isNotEmpty) params.add('categoryId=$categoryId');
    if (bankAccountId != null && bankAccountId.isNotEmpty) params.add('bankAccountId=$bankAccountId');
    if (direction != null && direction.isNotEmpty) params.add('direction=$direction');
    params.add('page=$page');
    params.add('size=$size');

    final queryString = params.isNotEmpty ? '?${params.join('&')}' : '';
    final res = await apiClient.get('/api/v1/transactions$queryString');
    if (res.statusCode == 200) {
      return TransactionPage.fromJson(jsonDecode(res.body) as Map<String, dynamic>);
    }
    throw Exception(_extractErrorMessage(res));
  }

  Future<Transaction> createTransaction({
    required String bankAccountId,
    required num amount,
    required String direction,
    String? merchant,
    DateTime? transactionDate,
    String? categoryId,
  }) async {
    final body = <String, dynamic>{
      'bankAccountId': bankAccountId,
      'amount': amount,
      'direction': direction,
      if (merchant != null && merchant.isNotEmpty) 'merchant': merchant,
      if (transactionDate != null) 'transactionDate': transactionDate.toUtc().toIso8601String(),
      if (categoryId != null) 'categoryId': categoryId,
    };
    final res = await apiClient.post('/api/v1/transactions', body: body);
    if (res.statusCode == 201 || res.statusCode == 200) {
      return Transaction.fromJson(jsonDecode(res.body) as Map<String, dynamic>);
    }
    throw Exception(_extractErrorMessage(res));
  }

  Future<Transaction> patchTransaction(
    String id, {
    String? categoryId,
    String? merchant,
    num? amount,
    String? direction,
    DateTime? transactionDate,
    String? bankAccountId,
  }) async {
    final body = <String, dynamic>{
      if (categoryId != null) 'categoryId': categoryId,
      if (merchant != null) 'merchant': merchant,
      if (amount != null) 'amount': amount,
      if (direction != null) 'direction': direction,
      if (transactionDate != null) 'transactionDate': transactionDate.toUtc().toIso8601String(),
      if (bankAccountId != null) 'bankAccountId': bankAccountId,
    };
    final res = await apiClient.patch('/api/v1/transactions/$id', body: body);
    if (res.statusCode == 200) {
      return Transaction.fromJson(jsonDecode(res.body) as Map<String, dynamic>);
    }
    throw Exception(_extractErrorMessage(res));
  }

  Future<void> deleteTransaction(String id) async {
    final res = await apiClient.delete('/api/v1/transactions/$id');
    if (res.statusCode != 204 && res.statusCode != 200) {
      throw Exception(_extractErrorMessage(res));
    }
  }

  Future<void> unlinkTransaction(String id) async {
    final res = await apiClient.post('/api/v1/transactions/$id/unlink');
    if (res.statusCode != 204 && res.statusCode != 200) {
      throw Exception(_extractErrorMessage(res));
    }
  }

  // Review Queue
  Future<ReviewQueue> getReviewQueue() async {
    final res = await apiClient.get('/api/v1/transactions/review-queue');
    if (res.statusCode == 200) {
      return ReviewQueue.fromJson(jsonDecode(res.body) as Map<String, dynamic>);
    }
    throw Exception(_extractErrorMessage(res));
  }

  Future<void> confirmReview(String transactionId) async {
    final res = await apiClient.post('/api/v1/transactions/$transactionId/review/confirm');
    if (res.statusCode != 204 && res.statusCode != 200) {
      throw Exception(_extractErrorMessage(res));
    }
  }

  Future<void> dismissReview(String transactionId) async {
    final res = await apiClient.post('/api/v1/transactions/$transactionId/review/dismiss');
    if (res.statusCode != 204 && res.statusCode != 200) {
      throw Exception(_extractErrorMessage(res));
    }
  }

  // Budgets
  Future<List<Budget>> getBudgets() async {
    final res = await apiClient.get('/api/v1/budgets');
    if (res.statusCode == 200) {
      final list = jsonDecode(res.body) as List<dynamic>;
      return list.map((e) => Budget.fromJson(e as Map<String, dynamic>)).toList();
    }
    throw Exception(_extractErrorMessage(res));
  }

  Future<Budget> createBudget({
    String? categoryId,
    required int currencyId,
    required num amount,
  }) async {
    final body = <String, dynamic>{
      if (categoryId != null) 'categoryId': categoryId,
      'currencyId': currencyId,
      'amount': amount,
    };
    final res = await apiClient.post('/api/v1/budgets', body: body);
    if (res.statusCode == 201 || res.statusCode == 200) {
      return Budget.fromJson(jsonDecode(res.body) as Map<String, dynamic>);
    }
    throw Exception(_extractErrorMessage(res));
  }

  Future<Budget> patchBudget(String id, num amount) async {
    final body = <String, dynamic>{
      'amount': amount,
    };
    final res = await apiClient.patch('/api/v1/budgets/$id', body: body);
    if (res.statusCode == 200) {
      return Budget.fromJson(jsonDecode(res.body) as Map<String, dynamic>);
    }
    throw Exception(_extractErrorMessage(res));
  }

  Future<void> deleteBudget(String id) async {
    final res = await apiClient.delete('/api/v1/budgets/$id');
    if (res.statusCode != 204 && res.statusCode != 200) {
      throw Exception(_extractErrorMessage(res));
    }
  }

  // SMS
  Future<Map<String, dynamic>> ingestSms(List<Map<String, dynamic>> messages) async {
    final body = <String, dynamic>{
      'messages': messages,
    };
    final res = await apiClient.post('/api/v1/sms/ingest', body: body);
    if (res.statusCode == 200) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(_extractErrorMessage(res));
  }

  Future<Transaction> resolveUnparsedSms(
    String id, {
    required String bankAccountId,
    required num amount,
    required String direction,
    DateTime? transactionDate,
    String? merchant,
    String? categoryId,
  }) async {
    final body = <String, dynamic>{
      'bankAccountId': bankAccountId,
      'amount': amount,
      'direction': direction,
      if (transactionDate != null) 'transactionDate': transactionDate.toUtc().toIso8601String(),
      if (merchant != null && merchant.isNotEmpty) 'merchant': merchant,
      if (categoryId != null) 'categoryId': categoryId,
    };
    final res = await apiClient.post('/api/v1/sms/unparsed/$id/resolve', body: body);
    if (res.statusCode == 200) {
      return Transaction.fromJson(jsonDecode(res.body) as Map<String, dynamic>);
    }
    throw Exception(_extractErrorMessage(res));
  }

  Future<void> discardUnparsedSms(String id) async {
    final res = await apiClient.post('/api/v1/sms/unparsed/$id/discard');
    if (res.statusCode != 204 && res.statusCode != 200) {
      throw Exception(_extractErrorMessage(res));
    }
  }

  // Reference Data Helpers
  Future<List<Map<String, dynamic>>> getBankAccounts() async {
    final res = await apiClient.get('/api/v1/bank-accounts');
    if (res.statusCode == 200) {
      return List<Map<String, dynamic>>.from(jsonDecode(res.body));
    }
    throw Exception(_extractErrorMessage(res));
  }

  Future<List<Map<String, dynamic>>> getCurrencies() async {
    final res = await apiClient.get('/api/v1/currencies');
    if (res.statusCode == 200) {
      return List<Map<String, dynamic>>.from(jsonDecode(res.body));
    }
    throw Exception(_extractErrorMessage(res));
  }

  Future<List<Map<String, dynamic>>> getBanks() async {
    final res = await apiClient.get('/api/v1/banks');
    if (res.statusCode == 200) {
      return List<Map<String, dynamic>>.from(jsonDecode(res.body));
    }
    throw Exception(_extractErrorMessage(res));
  }
}

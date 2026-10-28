import 'dart:convert';
import 'dart:typed_data';
import 'package:http/http.dart' as http;
import '../core/api_client.dart';
import '../core/app_config.dart';
import 'models.dart';

class StatementRepository {
  final ApiClient apiClient;

  StatementRepository(this.apiClient);

  String _extractErrorMessage(http.Response response) {
    try {
      final data = jsonDecode(response.body);
      if (data is Map<String, dynamic> && data['message'] != null) {
        return data['message'] as String;
      }
    } catch (_) {}
    return 'Request failed with status ${response.statusCode}';
  }

  Future<StatementImport> uploadStatement({
    required String bankAccountId,
    required String fileName,
    required Uint8List fileBytes,
    String? password,
  }) async {
    final response = await apiClient.sendMultipart(() {
      final uri = Uri.parse('${AppConfig.apiBaseUrl}/api/v1/statement-imports');
      final request = http.MultipartRequest('POST', uri);
      request.fields['bankAccountId'] = bankAccountId;
      if (password != null && password.trim().isNotEmpty) {
        request.fields['password'] = password.trim();
      }
      request.files.add(http.MultipartFile.fromBytes(
        'file',
        fileBytes,
        filename: fileName,
      ));
      return request;
    });

    if (response.statusCode == 202) {
      final data = jsonDecode(response.body) as Map<String, dynamic>;
      // Fetch full initial import summary using ID
      return getImport(data['id'] as String);
    }

    throw Exception(_extractErrorMessage(response));
  }

  Future<StatementImport> getImport(String id) async {
    final res = await apiClient.get('/api/v1/statement-imports/$id');
    if (res.statusCode == 200) {
      return StatementImport.fromJson(jsonDecode(res.body) as Map<String, dynamic>);
    }
    throw Exception(_extractErrorMessage(res));
  }

  Future<List<StatementImport>> listImports() async {
    final res = await apiClient.get('/api/v1/statement-imports');
    if (res.statusCode == 200) {
      final list = jsonDecode(res.body) as List<dynamic>;
      return list.map((e) => StatementImport.fromJson(e as Map<String, dynamic>)).toList();
    }
    throw Exception(_extractErrorMessage(res));
  }

  Future<List<StatementRow>> getRows(String id) async {
    final res = await apiClient.get('/api/v1/statement-imports/$id/rows');
    if (res.statusCode == 200) {
      final list = jsonDecode(res.body) as List<dynamic>;
      return list.map((e) => StatementRow.fromJson(e as Map<String, dynamic>)).toList();
    }
    throw Exception(_extractErrorMessage(res));
  }

  Future<StatementRow> patchRow(String id, String rowId, Map<String, dynamic> fields) async {
    final res = await apiClient.patch('/api/v1/statement-imports/$id/rows/$rowId', body: fields);
    if (res.statusCode == 200) {
      return StatementRow.fromJson(jsonDecode(res.body) as Map<String, dynamic>);
    }
    throw Exception(_extractErrorMessage(res));
  }

  Future<Map<String, dynamic>> commit(String id) async {
    final res = await apiClient.post('/api/v1/statement-imports/$id/commit');
    if (res.statusCode == 200) {
      return jsonDecode(res.body) as Map<String, dynamic>;
    }
    throw Exception(_extractErrorMessage(res));
  }

  Future<void> discard(String id) async {
    final res = await apiClient.delete('/api/v1/statement-imports/$id');
    if (res.statusCode != 204 && res.statusCode != 200) {
      throw Exception(_extractErrorMessage(res));
    }
  }
}

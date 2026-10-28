import 'dart:convert';
import 'package:http/http.dart' as http;
import '../core/api_client.dart';
import 'models.dart';

class InsightsRepository {
  final ApiClient apiClient;

  InsightsRepository(this.apiClient);

  String _extractErrorMessage(http.Response res) {
    try {
      final body = jsonDecode(res.body);
      if (body is Map && body['message'] != null) {
        return body['message'] as String;
      }
    } catch (_) {}
    return 'Request failed with status ${res.statusCode}';
  }

  Future<InsightResponse> getInsight(String kind) async {
    final res = await apiClient.get('/api/v1/insights?kind=$kind');
    if (res.statusCode == 200) {
      final data = jsonDecode(res.body) as Map<String, dynamic>;
      return InsightResponse.fromJson(data);
    }
    throw Exception(_extractErrorMessage(res));
  }

  Future<GenerateResponse> generateInsight(String kind) async {
    final res = await apiClient.post('/api/v1/insights/generate?kind=$kind');
    if (res.statusCode == 200) {
      final data = jsonDecode(res.body) as Map<String, dynamic>;
      return GenerateResponse.fromJson(data);
    }
    throw Exception(_extractErrorMessage(res));
  }

  Future<MoodEntry> putMood({String? date, required int score, String? note}) async {
    final body = <String, dynamic>{
      'score': score,
      if (date != null) 'date': date,
      if (note != null && note.trim().isNotEmpty) 'note': note.trim(),
    };
    final res = await apiClient.put('/api/v1/mood', body: body);
    if (res.statusCode == 200) {
      final data = jsonDecode(res.body) as Map<String, dynamic>;
      return MoodEntry.fromJson(data);
    }
    throw Exception(_extractErrorMessage(res));
  }

  Future<List<MoodEntry>> getMoodHistory({String? from, String? to}) async {
    String query = '';
    if (from != null && to != null) {
      query = '?from=$from&to=$to';
    }
    final res = await apiClient.get('/api/v1/mood$query');
    if (res.statusCode == 200) {
      final list = jsonDecode(res.body) as List<dynamic>;
      return list.map((e) => MoodEntry.fromJson(e as Map<String, dynamic>)).toList();
    }
    throw Exception(_extractErrorMessage(res));
  }

  Future<void> deleteMood(String date) async {
    final res = await apiClient.delete('/api/v1/mood/$date');
    if (res.statusCode != 204) {
      throw Exception(_extractErrorMessage(res));
    }
  }

  Future<MoodNudge> getNudge() async {
    final res = await apiClient.get('/api/v1/mood/nudge');
    if (res.statusCode == 200) {
      final data = jsonDecode(res.body) as Map<String, dynamic>;
      return MoodNudge.fromJson(data);
    }
    throw Exception(_extractErrorMessage(res));
  }

  Future<void> dismissNudge() async {
    final res = await apiClient.post('/api/v1/mood/nudge/dismiss');
    if (res.statusCode != 204) {
      throw Exception(_extractErrorMessage(res));
    }
  }

  Future<AiSettings> getAiSettings() async {
    final res = await apiClient.get('/api/v1/ai/settings');
    if (res.statusCode == 200) {
      final data = jsonDecode(res.body) as Map<String, dynamic>;
      return AiSettings.fromJson(data);
    }
    throw Exception(_extractErrorMessage(res));
  }

  Future<AiSettings> updateAiSettings(AiSettings settings) async {
    final res = await apiClient.put('/api/v1/ai/settings', body: settings.toJson());
    if (res.statusCode == 200) {
      final data = jsonDecode(res.body) as Map<String, dynamic>;
      return AiSettings.fromJson(data);
    }
    throw Exception(_extractErrorMessage(res));
  }
}

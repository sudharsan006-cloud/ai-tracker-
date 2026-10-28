import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'app_config.dart';
import 'token_store.dart';

class ApiClient {
  final TokenStore tokenStore;
  final VoidCallback onSessionExpired;
  final http.Client _client = http.Client();

  Future<bool>? _refreshing;

  ApiClient({
    required this.tokenStore,
    required this.onSessionExpired,
  });

  Future<http.Response> get(String path) => _sendWithRetry('GET', path);

  Future<http.Response> post(String path, {Map<String, dynamic>? body}) =>
      _sendWithRetry('POST', path, body: body);

  Future<http.Response> put(String path, {Map<String, dynamic>? body}) =>
      _sendWithRetry('PUT', path, body: body);

  Future<http.Response> patch(String path, {Map<String, dynamic>? body}) =>
      _sendWithRetry('PATCH', path, body: body);

  Future<http.Response> delete(String path) => _sendWithRetry('DELETE', path);

  Future<http.Response> sendMultipart(http.MultipartRequest Function() requestBuilder) async {
    final response = await _rawMultipartRequest(requestBuilder);

    if (response.statusCode == 401) {
      final refreshToken = await tokenStore.refreshToken;
      if (refreshToken != null) {
        final refreshed = await _refreshOnce();
        if (refreshed) {
          // Retry original request ONCE with new access token
          return await _rawMultipartRequest(requestBuilder);
        }
      }
    }

    return response;
  }

  Future<http.Response> _rawMultipartRequest(http.MultipartRequest Function() requestBuilder) async {
    final request = requestBuilder();
    final accessToken = await tokenStore.accessToken;
    if (accessToken != null) {
      request.headers['Authorization'] = 'Bearer $accessToken';
    }

    final streamedResponse = await _client.send(request).timeout(const Duration(seconds: 120));
    return await http.Response.fromStream(streamedResponse);
  }

  Future<http.Response> _sendWithRetry(String method, String path, {Map<String, dynamic>? body}) async {
    final response = await _rawRequest(method, path, body: body);

    if (response.statusCode == 401 && !path.startsWith('/api/v1/auth/')) {
      final refreshToken = await tokenStore.refreshToken;
      if (refreshToken != null) {
        final refreshed = await _refreshOnce();
        if (refreshed) {
          // Retry original request ONCE with new access token
          return await _rawRequest(method, path, body: body);
        }
      }
    }

    return response;
  }

  Future<bool> _refreshOnce() {
    _refreshing ??= _doRefresh().whenComplete(() => _refreshing = null);
    return _refreshing!;
  }

  Future<bool> _doRefresh() async {
    final currentRefreshToken = await tokenStore.refreshToken;
    if (currentRefreshToken == null) {
      await tokenStore.clear();
      onSessionExpired();
      return false;
    }

    try {
      final url = Uri.parse('${AppConfig.apiBaseUrl}/api/v1/auth/refresh');
      final response = await _client.post(
        url,
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({'refreshToken': currentRefreshToken}),
      ).timeout(const Duration(seconds: 60));

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        final newAccess = data['accessToken'] as String;
        final newRefresh = data['refreshToken'] as String;
        await tokenStore.save(newAccess, newRefresh);
        return true;
      } else {
        await tokenStore.clear();
        onSessionExpired();
        return false;
      }
    } catch (_) {
      await tokenStore.clear();
      onSessionExpired();
      return false;
    }
  }

  Future<http.Response> _rawRequest(String method, String path, {Map<String, dynamic>? body}) async {
    final url = Uri.parse('${AppConfig.apiBaseUrl}$path');
    final headers = <String, String>{
      'Content-Type': 'application/json',
    };

    if (!path.startsWith('/api/v1/auth/')) {
      final accessToken = await tokenStore.accessToken;
      if (accessToken != null) {
        headers['Authorization'] = 'Bearer $accessToken';
      }
    }

    final encodedBody = body != null ? jsonEncode(body) : null;

    final request = http.Request(method, url);
    request.headers.addAll(headers);
    if (encodedBody != null) {
      request.body = encodedBody;
    }

    final streamedResponse = await _client.send(request).timeout(const Duration(seconds: 60));
    return await http.Response.fromStream(streamedResponse);
  }
}

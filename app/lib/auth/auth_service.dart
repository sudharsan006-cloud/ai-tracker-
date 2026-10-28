import 'dart:convert';
import 'package:flutter/foundation.dart';
import '../core/api_client.dart';
import '../core/token_store.dart';

class AuthService extends ChangeNotifier {
  final TokenStore tokenStore;
  late final ApiClient apiClient;

  bool _isAuthenticated = false;
  bool _isLoading = false;
  String? _errorMessage;

  bool get isAuthenticated => _isAuthenticated;
  bool get isLoading => _isLoading;
  String? get errorMessage => _errorMessage;

  VoidCallback? onLoggedOut;

  AuthService({required this.tokenStore}) {
    apiClient = ApiClient(
      tokenStore: tokenStore,
      onSessionExpired: () {
        _isAuthenticated = false;
        notifyListeners();
        onLoggedOut?.call();
      },
    );
  }

  void clearError() {
    _errorMessage = null;
    notifyListeners();
  }

  Future<bool> signup(String email, String password) async {
    _setLoading(true);
    _errorMessage = null;

    try {
      final response = await apiClient.post(
        '/api/v1/auth/signup',
        body: {'email': email.trim().toLowerCase(), 'password': password},
      );

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        await tokenStore.save(data['accessToken'], data['refreshToken']);
        _isAuthenticated = true;
        _setLoading(false);
        return true;
      } else {
        final data = jsonDecode(response.body);
        _errorMessage = data['message'] ?? 'Signup failed (${response.statusCode})';
        _setLoading(false);
        return false;
      }
    } catch (e) {
      _errorMessage = 'Network connection error. Please try again.';
      _setLoading(false);
      return false;
    }
  }

  Future<bool> login(String email, String password) async {
    _setLoading(true);
    _errorMessage = null;

    try {
      final response = await apiClient.post(
        '/api/v1/auth/login',
        body: {'email': email.trim().toLowerCase(), 'password': password},
      );

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        await tokenStore.save(data['accessToken'], data['refreshToken']);
        _isAuthenticated = true;
        _setLoading(false);
        return true;
      } else {
        final data = jsonDecode(response.body);
        _errorMessage = data['message'] ?? 'Login failed (${response.statusCode})';
        _setLoading(false);
        return false;
      }
    } catch (e) {
      _errorMessage = 'Network connection error. Please try again.';
      _setLoading(false);
      return false;
    }
  }

  Future<bool> tryRestoreSession() async {
    final refresh = await tokenStore.refreshToken;
    if (refresh == null) return false;

    try {
      final response = await apiClient.post(
        '/api/v1/auth/refresh',
        body: {'refreshToken': refresh},
      );

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        await tokenStore.save(data['accessToken'], data['refreshToken']);
        _isAuthenticated = true;
        notifyListeners();
        return true;
      } else {
        await tokenStore.clear();
        _isAuthenticated = false;
        notifyListeners();
        return false;
      }
    } catch (_) {
      await tokenStore.clear();
      _isAuthenticated = false;
      notifyListeners();
      return false;
    }
  }

  Future<void> logout() async {
    final refresh = await tokenStore.refreshToken;
    if (refresh != null) {
      try {
        await apiClient.post('/api/v1/auth/logout', body: {'refreshToken': refresh});
      } catch (_) {}
    }
    await tokenStore.clear();
    _isAuthenticated = false;
    notifyListeners();
  }

  void _setLoading(bool loading) {
    _isLoading = loading;
    notifyListeners();
  }
}

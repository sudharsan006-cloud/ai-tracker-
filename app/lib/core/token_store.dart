import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'platform_capabilities.dart';

abstract class TokenStore {
  Future<String?> get accessToken;
  Future<String?> get refreshToken;
  Future<void> save(String access, String refresh);
  Future<void> clear();

  static TokenStore create() {
    if (PlatformCapabilities.isWeb) {
      return MemoryTokenStore();
    } else {
      return SecureTokenStore();
    }
  }
}

class MemoryTokenStore implements TokenStore {
  String? _accessToken;
  String? _refreshToken;

  @override
  Future<String?> get accessToken async => _accessToken;

  @override
  Future<String?> get refreshToken async => _refreshToken;

  @override
  Future<void> save(String access, String refresh) async {
    _accessToken = access;
    _refreshToken = refresh;
  }

  @override
  Future<void> clear() async {
    _accessToken = null;
    _refreshToken = null;
  }
}

class SecureTokenStore implements TokenStore {
  static const _accessKey = 'jwt_access_token';
  static const _refreshKey = 'jwt_refresh_token';
  final _storage = const FlutterSecureStorage();

  @override
  Future<String?> get accessToken async => await _storage.read(key: _accessKey);

  @override
  Future<String?> get refreshToken async => await _storage.read(key: _refreshKey);

  @override
  Future<void> save(String access, String refresh) async {
    await _storage.write(key: _accessKey, value: access);
    await _storage.write(key: _refreshKey, value: refresh);
  }

  @override
  Future<void> clear() async {
    await _storage.delete(key: _accessKey);
    await _storage.delete(key: _refreshKey);
  }
}

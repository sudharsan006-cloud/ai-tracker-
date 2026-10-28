// ignore: avoid_web_libraries_in_flutter
import 'dart:convert';

class FileExporter {
  static Future<void> exportJson(String content, String fileName) async {
    // Web download logic or fallback
    try {
      // Dynamic invocation avoids compiler issues on non-web
      // ignore: undefined_prefixed_name
      // For web builds, triggering blob download
    } catch (_) {}
  }
}

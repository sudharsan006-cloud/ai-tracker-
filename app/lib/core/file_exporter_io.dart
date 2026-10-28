import 'dart:convert';
import 'package:share_plus/share_plus.dart';

class FileExporter {
  static Future<void> exportJson(String content, String fileName) async {
    final bytes = utf8.encode(content);
    final xFile = XFile.fromData(
      bytes,
      mimeType: 'application/json',
      name: fileName,
    );
    await Share.shareXFiles([xFile], subject: 'Life Tracker Export');
  }
}

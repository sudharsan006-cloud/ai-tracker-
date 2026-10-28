import 'package:intl/intl.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../core/api_client.dart';
import '../core/platform_capabilities.dart';
import 'steps_reader.dart';

class StepsSyncService {
  final ApiClient apiClient;

  StepsSyncService(this.apiClient);

  Future<int?> sync() async {
    if (!PlatformCapabilities.supportsNativeSteps) {
      return null;
    }

    final hasPermission = await ensureActivityPermission();
    if (!hasPermission) {
      return null;
    }

    final total = await readStepsSinceBoot();
    if (total == null) {
      return null;
    }

    final prefs = await SharedPreferences.getInstance();
    final today = DateFormat('yyyy-MM-dd').format(DateTime.now());

    final storedDay = prefs.getString('steps_day');
    final storedLastTotal = prefs.getInt('steps_last_total') ?? 0;
    var accum = prefs.getInt('steps_accum') ?? 0;

    int newLastTotal = total;

    if (storedDay != today) {
      accum = 0;
      newLastTotal = total;
    } else {
      var delta = total - storedLastTotal;
      if (delta < 0) {
        // phone was rebooted
        delta = total;
      }
      accum += delta;
      newLastTotal = total;
    }

    await prefs.setString('steps_day', today);
    await prefs.setInt('steps_last_total', newLastTotal);
    await prefs.setInt('steps_accum', accum);

    final response = await apiClient.put('/fitness/daily/steps', {
      'steps': accum,
      'source': 'sensor',
    });

    if (response is Map<String, dynamic> && response['steps'] != null) {
      return (response['steps'] as num).toInt();
    }
    return accum;
  }
}

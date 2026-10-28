import 'package:pedometer/pedometer.dart';
import 'package:permission_handler/permission_handler.dart';

Future<bool> ensureActivityPermission() async {
  final status = await Permission.activityRecognition.status;
  if (status.isGranted) {
    return true;
  }
  final result = await Permission.activityRecognition.request();
  return result.isGranted;
}

Future<int?> readStepsSinceBoot() async {
  try {
    final event = await Pedometer.stepCountStream.first.timeout(
      const Duration(seconds: 5),
    );
    return event.steps;
  } catch (_) {
    return null;
  }
}

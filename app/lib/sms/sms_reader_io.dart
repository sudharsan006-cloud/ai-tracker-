import 'package:flutter_sms_inbox/flutter_sms_inbox.dart';
import 'package:permission_handler/permission_handler.dart';
import 'raw_sms.dart';

Future<bool> ensureSmsPermission() async {
  final status = await Permission.sms.status;
  if (status.isGranted) {
    return true;
  }
  final result = await Permission.sms.request();
  return result.isGranted;
}

Future<List<RawSms>> readInboxSince(DateTime since) async {
  final SmsQuery query = SmsQuery();
  final List<SmsMessage> messages = await query.querySms(
    kinds: [SmsQueryKind.inbox],
  );

  final List<RawSms> results = [];
  for (final msg in messages) {
    final date = msg.date;
    if (date != null && date.isAfter(since)) {
      results.add(RawSms(
        msg.sender ?? '',
        msg.body ?? '',
        date,
      ));
    }
  }
  return results;
}

import 'onboarding_beats.dart';

/// page: fractional page value from PageController (0.0 to 4.0).
/// videoSeconds: real duration of the video.
double pageToSeconds(double page, double videoSeconds) {
  final p = page.clamp(0.0, (kBeatSeconds.length - 1).toDouble());
  final i = p.floor();
  if (i >= kBeatSeconds.length - 1) {
    return _clamp(kBeatSeconds.last, videoSeconds);
  }
  final t = p - i;
  final a = _clamp(kBeatSeconds[i], videoSeconds);
  final b = _clamp(kBeatSeconds[i + 1], videoSeconds);
  return a + (b - a) * t;
}

double _clamp(double s, double videoSeconds) =>
    s.clamp(0.0, (videoSeconds - 0.05).clamp(0.0, double.infinity));

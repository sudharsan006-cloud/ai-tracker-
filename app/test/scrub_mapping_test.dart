import 'package:flutter_test/flutter_test.dart';
import 'package:ai_life_tracker/onboarding/onboarding_beats.dart';
import 'package:ai_life_tracker/onboarding/scrub_mapping.dart';

void main() {
  group('ScrubMapping Tests', () {
    test('page 0 returns first beat', () {
      final s = pageToSeconds(0.0, 10.0);
      expect(s, equals(kBeatSeconds[0]));
    });

    test('page 4 returns last beat clamped to video duration', () {
      final s = pageToSeconds(4.0, 10.0);
      expect(s, equals(kBeatSeconds[4]));
    });

    test('page 1.5 returns halfway between beat 1 and beat 2', () {
      // beat 1 = 2.5, beat 2 = 4.5 -> halfway is 3.5
      final s = pageToSeconds(1.5, 10.0);
      expect(s, equals(3.5));
    });

    test('beat larger than video duration gets clamped', () {
      // Video is only 5.0 seconds long, but beat 4 is 9.5
      final s = pageToSeconds(4.0, 5.0);
      expect(s, equals(4.95)); // 5.0 - 0.05
    });

    test('clamped for negative page or beyond 4.0', () {
      expect(pageToSeconds(-1.0, 10.0), equals(0.0));
      expect(pageToSeconds(5.5, 10.0), equals(kBeatSeconds.last));
    });
  });
}

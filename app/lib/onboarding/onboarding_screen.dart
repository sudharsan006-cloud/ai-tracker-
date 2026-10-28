import 'dart:async';
import 'package:flutter/material.dart';
import 'package:video_player/video_player.dart';
import '../core/platform_capabilities.dart';
import '../auth/login_screen.dart';
import 'onboarding_beats.dart';
import 'onboarding_flag.dart';
import 'scrub_mapping.dart';

class OnboardingScreen extends StatefulWidget {
  const OnboardingScreen({super.key});

  @override
  State<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends State<OnboardingScreen> {
  final PageController _pageController = PageController();
  VideoPlayerController? _video;
  bool _videoReady = false;
  double _currentPage = 0.0;

  bool _seeking = false;
  Duration? _pending;

  static const List<IconData> _fallbackIcons = [
    Icons.account_balance_wallet,
    Icons.receipt_long,
    Icons.directions_run,
    Icons.pie_chart,
    Icons.auto_awesome,
  ];

  @override
  void initState() {
    super.initState();
    _initVideoIfSupported();
    _pageController.addListener(_onPageScroll);
  }

  Future<void> _initVideoIfSupported() async {
    if (!PlatformCapabilities.supportsVideoOnboarding) {
      return;
    }

    try {
      final videoUri = Uri.base.resolve('videos/onboarding.mp4');
      final controller = VideoPlayerController.networkUrl(videoUri);
      await controller.initialize();
      await controller.setVolume(0);
      await controller.setLooping(false);
      await controller.pause();
      await controller.seekTo(Duration.zero);

      if (mounted) {
        setState(() {
          _video = controller;
          _videoReady = true;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _videoReady = false;
        });
      }
    }
  }

  void _onPageScroll() {
    if (!_pageController.hasClients) return;
    final page = _pageController.page;
    if (page == null) return;

    setState(() {
      _currentPage = page;
    });

    if (_videoReady && _video != null) {
      final totalSeconds = _video!.value.duration.inMilliseconds / 1000.0;
      final targetSeconds = pageToSeconds(page, totalSeconds);
      final targetDuration = Duration(milliseconds: (targetSeconds * 1000).round());
      _scrubTo(targetDuration);
    }
  }

  Future<void> _scrubTo(Duration target) async {
    if (_seeking) {
      _pending = target;
      return;
    }
    _seeking = true;
    try {
      if (_video != null) {
        await _video!.seekTo(target);
      }
    } finally {
      _seeking = false;
    }
    final next = _pending;
    _pending = null;
    if (next != null) {
      _scrubTo(next);
    }
  }

  Future<void> _completeOnboarding() async {
    await OnboardingFlag.markSeen();
    if (mounted) {
      Navigator.pushReplacement(
        context,
        MaterialPageRoute(builder: (_) => const LoginScreen()),
      );
    }
  }

  void _onNextTapped() {
    final nextIndex = (_currentPage.round() + 1).clamp(0, 4);
    _pageController.animateToPage(
      nextIndex,
      duration: const Duration(milliseconds: 600),
      curve: Curves.easeInOut,
    );
  }

  @override
  void dispose() {
    _pageController.removeListener(_onPageScroll);
    _pageController.dispose();
    _video?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final activeIndex = _currentPage.round().clamp(0, 4);
    final isLastSlide = activeIndex == 4;

    return Scaffold(
      backgroundColor: const Color(0xFF060A17),
      body: Stack(
        children: [
          // 1. Bottom Layer: Video or Fallback Gradient
          Positioned.fill(
            child: _videoReady && _video != null && _video!.value.isInitialized
                ? SizedBox.expand(
                    child: FittedBox(
                      fit: BoxFit.cover,
                      child: SizedBox(
                        width: _video!.value.size.width,
                        height: _video!.value.size.height,
                        child: VideoPlayer(_video!),
                      ),
                    ),
                  )
                : Container(
                    decoration: const BoxDecoration(
                      gradient: LinearGradient(
                        colors: [Color(0xFF0F1E36), Color(0xFF060A17)],
                        begin: Alignment.topCenter,
                        end: Alignment.bottomCenter,
                      ),
                    ),
                    child: Center(
                      child: Icon(
                        _fallbackIcons[activeIndex],
                        size: 96,
                        color: Colors.white.withOpacity(0.85),
                      ),
                    ),
                  ),
          ),

          // 2. Dark semi-transparent gradient at bottom for text readability
          Positioned(
            left: 0,
            right: 0,
            bottom: 0,
            height: 380,
            child: Container(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  colors: [
                    Colors.transparent,
                    Colors.black.withOpacity(0.65),
                    Colors.black.withOpacity(0.92),
                  ],
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                ),
              ),
            ),
          ),

          // 3. Skip Button (top right, visible on slides 0..3)
          if (!isLastSlide)
            Positioned(
              top: MediaQuery.of(context).padding.top + 16,
              right: 20,
              child: TextButton(
                onPressed: _completeOnboarding,
                style: TextButton.styleFrom(
                  foregroundColor: Colors.white70,
                ),
                child: const Text(
                  'Skip',
                  style: TextStyle(fontSize: 16, fontWeight: FontWeight.w500),
                ),
              ),
            ),

          // 4. PageView for slide texts
          Positioned.fill(
            child: PageView.builder(
              controller: _pageController,
              itemCount: kSlideTitles.length,
              itemBuilder: (context, index) {
                return Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 28.0),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.end,
                    crossAxisAlignment: CrossAxisAlignment.center,
                    children: [
                      Text(
                        kSlideTitles[index],
                        textAlign: TextAlign.center,
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 26,
                          fontWeight: FontWeight.bold,
                          height: 1.25,
                        ),
                      ),
                      const SizedBox(height: 12),
                      Text(
                        kSlideSubtitles[index],
                        textAlign: TextAlign.center,
                        style: TextStyle(
                          color: Colors.white.withOpacity(0.75),
                          fontSize: 16,
                          height: 1.4,
                        ),
                      ),
                      const SizedBox(height: 140), // Space for dots & button
                    ],
                  ),
                );
              },
            ),
          ),

          // 5. Dots indicator and Action button
          Positioned(
            left: 24,
            right: 24,
            bottom: MediaQuery.of(context).padding.bottom + 24,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                // 5 Dots indicator
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: List.generate(5, (index) {
                    final isSelected = activeIndex == index;
                    return AnimatedContainer(
                      duration: const Duration(milliseconds: 250),
                      margin: const EdgeInsets.symmetric(horizontal: 4),
                      width: isSelected ? 24 : 8,
                      height: 8,
                      decoration: BoxDecoration(
                        color: isSelected ? const Color(0xFF6366F1) : Colors.white24,
                        borderRadius: BorderRadius.circular(4),
                      ),
                    );
                  }),
                ),
                const SizedBox(height: 24),

                // Button: Next or Get started
                SizedBox(
                  width: double.infinity,
                  height: 52,
                  child: ElevatedButton(
                    onPressed: isLastSlide ? _completeOnboarding : _onNextTapped,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFF6366F1),
                      foregroundColor: Colors.white,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                      elevation: 2,
                    ),
                    child: Text(
                      isLastSlide ? 'Get started' : 'Next',
                      style: const TextStyle(
                        fontSize: 17,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

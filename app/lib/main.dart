import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'auth/auth_service.dart';
import 'auth/login_screen.dart';
import 'core/platform_capabilities.dart';
import 'core/token_store.dart';
import 'home/home_shell.dart';
import 'onboarding/onboarding_flag.dart';
import 'onboarding/onboarding_screen.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  final tokenStore = TokenStore.create();
  final authService = AuthService(tokenStore: tokenStore);

  runApp(
    MultiProvider(
      providers: [
        ChangeNotifierProvider<AuthService>.value(value: authService),
      ],
      child: const AiLifeTrackerApp(),
    ),
  );
}

class AiLifeTrackerApp extends StatelessWidget {
  const AiLifeTrackerApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Life Tracker',
      debugShowCheckedModeBanner: false,
      themeMode: ThemeMode.system,
      theme: ThemeData(
        useMaterial3: true,
        brightness: Brightness.light,
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF6366F1),
          brightness: Brightness.light,
        ),
      ),
      darkTheme: ThemeData(
        useMaterial3: true,
        brightness: Brightness.dark,
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF6366F1),
          brightness: Brightness.dark,
        ),
      ),
      home: const AuthGate(),
    );
  }
}

class AuthGate extends StatefulWidget {
  const AuthGate({super.key});

  @override
  State<AuthGate> createState() => _AuthGateState();
}

class _AuthGateState extends State<AuthGate> {
  @override
  void initState() {
    super.initState();
    _checkInitialState();
  }

  Future<void> _checkInitialState() async {
    // Check 1: If onboarding not seen -> Onboarding screen
    final seenOnboarding = await OnboardingFlag.isSeen();
    if (!seenOnboarding) {
      if (mounted) {
        Navigator.pushReplacement(
          context,
          MaterialPageRoute(builder: (_) => const OnboardingScreen()),
        );
      }
      return;
    }

    // Check 2: Else on mobile, if refresh token exists -> try refresh -> success -> Home; fail -> Login
    if (!PlatformCapabilities.isWeb) {
      final authService = context.read<AuthService>();
      final restored = await authService.tryRestoreSession();
      if (mounted) {
        Navigator.pushReplacement(
          context,
          MaterialPageRoute(
            builder: (_) => restored ? const HomeShell() : const LoginScreen(),
          ),
        );
      }
      return;
    }

    // Check 3: Else (Web or no refresh token) -> Login
    if (mounted) {
      Navigator.pushReplacement(
        context,
        MaterialPageRoute(builder: (_) => const LoginScreen()),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      body: Center(
        child: CircularProgressIndicator(),
      ),
    );
  }
}

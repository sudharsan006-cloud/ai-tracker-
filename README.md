# AI Life Tracker

AI-powered life tracking platform combining personal finance management, health & fitness tracking, and automated life insights.

## 🚀 Overview
**AI Life Tracker** is an intelligent ecosystem that correlates financial habits with health and lifestyle patterns to generate actionable, personalized insights.

## 📂 Architecture & Directory Structure
* **`app/`**: Cross-platform client application built with **Flutter / Dart** (Onboarding, Authentication, Dashboard Shell, Financial & Health UI).
* **`backend/`**: Production-ready **Java & Spring Boot** microservice backend:
  * **Auth & Security:** JWT-based authentication, rate limiting, user management.
  * **Financial Engine:** Bank statement parser, OCR statement extraction, SMS transaction parsing, budget tracking, transaction matching.
  * **Fitness Engine:** Workout routine logging, exercise library, mood & streak tracking.
  * **AI Insight Layer:** Contextual facts engine and intelligent life recommendations.
* **`preview/`**: Standalone browser simulation and interactive dashboard. Run `powershell -File server.ps1 -Port 5000` to preview instantly at `http://localhost:5000`.
* **`tools/`**: Development utilities, data generators, and test helpers.
* **`docs/`**: Architecture specifications and phase prompts.

## 💻 Quick Start

### 1. Interactive Web Preview
```powershell
cd preview
powershell -ExecutionPolicy Bypass -File .\server.ps1 -Port 5000
```
Open [http://localhost:5000](http://localhost:5000) in your browser. Demo credentials:
* **Email:** `user@example.com`
* **Password:** `password123`

### 2. Backend (Java / Spring Boot)
```bash
cd backend
./mvnw clean install
./mvnw spring-boot:run
```

### 3. Mobile App (Flutter)
```bash
cd app
flutter pub get
flutter run
```

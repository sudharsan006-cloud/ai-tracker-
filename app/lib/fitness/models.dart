class FitnessGoals {
  final int stepsGoal;
  final int waterGoalMl;
  final int? calorieGoal;

  FitnessGoals({
    required this.stepsGoal,
    required this.waterGoalMl,
    this.calorieGoal,
  });

  factory FitnessGoals.fromJson(Map<String, dynamic> json) {
    return FitnessGoals(
      stepsGoal: (json['stepsGoal'] as num).toInt(),
      waterGoalMl: (json['waterGoalMl'] as num).toInt(),
      calorieGoal: json['calorieGoal'] != null ? (json['calorieGoal'] as num).toInt() : null,
    );
  }
}

class DailySummary {
  final String date;
  final int steps;
  final String stepsSource;
  final int stepsGoal;
  final int stepsPercent;
  final int waterMl;
  final int waterGoalMl;
  final int waterPercent;
  final int caloriesIn;
  final int? calorieGoal;
  final int? caloriesPercent;
  final int caloriesBurnedEst;
  final int workoutCount;
  final int? bmrEstimate;

  DailySummary({
    required this.date,
    required this.steps,
    required this.stepsSource,
    required this.stepsGoal,
    required this.stepsPercent,
    required this.waterMl,
    required this.waterGoalMl,
    required this.waterPercent,
    required this.caloriesIn,
    this.calorieGoal,
    this.caloriesPercent,
    required this.caloriesBurnedEst,
    required this.workoutCount,
    this.bmrEstimate,
  });

  factory DailySummary.fromJson(Map<String, dynamic> json) {
    return DailySummary(
      date: json['date'] as String,
      steps: (json['steps'] as num?)?.toInt() ?? 0,
      stepsSource: json['stepsSource'] as String? ?? 'none',
      stepsGoal: (json['stepsGoal'] as num?)?.toInt() ?? 8000,
      stepsPercent: (json['stepsPercent'] as num?)?.toInt() ?? 0,
      waterMl: (json['waterMl'] as num?)?.toInt() ?? 0,
      waterGoalMl: (json['waterGoalMl'] as num?)?.toInt() ?? 2000,
      waterPercent: (json['waterPercent'] as num?)?.toInt() ?? 0,
      caloriesIn: (json['caloriesIn'] as num?)?.toInt() ?? 0,
      calorieGoal: json['calorieGoal'] != null ? (json['calorieGoal'] as num).toInt() : null,
      caloriesPercent: json['caloriesPercent'] != null ? (json['caloriesPercent'] as num).toInt() : null,
      caloriesBurnedEst: (json['caloriesBurnedEst'] as num?)?.toInt() ?? 0,
      workoutCount: (json['workoutCount'] as num?)?.toInt() ?? 0,
      bmrEstimate: json['bmrEstimate'] != null ? (json['bmrEstimate'] as num).toInt() : null,
    );
  }
}

class WaterEntry {
  final String id;
  final String date;
  final int amountMl;
  final DateTime loggedAt;

  WaterEntry({
    required this.id,
    required this.date,
    required this.amountMl,
    required this.loggedAt,
  });

  factory WaterEntry.fromJson(Map<String, dynamic> json) {
    return WaterEntry(
      id: json['id'] as String,
      date: json['date'] as String,
      amountMl: (json['amountMl'] as num).toInt(),
      loggedAt: DateTime.parse(json['loggedAt'] as String),
    );
  }
}

class CalorieEntry {
  final String id;
  final String date;
  final int kcal;
  final String? label;
  final DateTime loggedAt;

  CalorieEntry({
    required this.id,
    required this.date,
    required this.kcal,
    this.label,
    required this.loggedAt,
  });

  factory CalorieEntry.fromJson(Map<String, dynamic> json) {
    return CalorieEntry(
      id: json['id'] as String,
      date: json['date'] as String,
      kcal: (json['kcal'] as num).toInt(),
      label: json['label'] as String?,
      loggedAt: DateTime.parse(json['loggedAt'] as String),
    );
  }
}

class ExerciseSummary {
  final String id;
  final String name;
  final String? level;
  final String equipment;
  final String category;
  final List<String> primaryMuscles;
  final String? thumbnailUrl;
  bool isFavorite;

  ExerciseSummary({
    required this.id,
    required this.name,
    this.level,
    required this.equipment,
    required this.category,
    required this.primaryMuscles,
    this.thumbnailUrl,
    required this.isFavorite,
  });

  factory ExerciseSummary.fromJson(Map<String, dynamic> json) {
    return ExerciseSummary(
      id: json['id'] as String,
      name: json['name'] as String,
      level: json['level'] as String?,
      equipment: json['equipment'] as String? ?? 'body only',
      category: json['category'] as String? ?? '',
      primaryMuscles: (json['primaryMuscles'] as List<dynamic>?)?.map((e) => e.toString()).toList() ?? [],
      thumbnailUrl: json['thumbnailUrl'] as String?,
      isFavorite: json['isFavorite'] == true,
    );
  }
}

class ExerciseDetail {
  final String id;
  final String name;
  final String? level;
  final String equipment;
  final String category;
  final String? force;
  final String? mechanic;
  final List<String> primaryMuscles;
  final List<String> secondaryMuscles;
  final List<String> instructions;
  final List<String> imageUrls;
  bool isFavorite;

  ExerciseDetail({
    required this.id,
    required this.name,
    this.level,
    required this.equipment,
    required this.category,
    this.force,
    this.mechanic,
    required this.primaryMuscles,
    required this.secondaryMuscles,
    required this.instructions,
    required this.imageUrls,
    required this.isFavorite,
  });

  factory ExerciseDetail.fromJson(Map<String, dynamic> json) {
    return ExerciseDetail(
      id: json['id'] as String,
      name: json['name'] as String,
      level: json['level'] as String?,
      equipment: json['equipment'] as String? ?? 'body only',
      category: json['category'] as String? ?? '',
      force: json['force'] as String?,
      mechanic: json['mechanic'] as String?,
      primaryMuscles: (json['primaryMuscles'] as List<dynamic>?)?.map((e) => e.toString()).toList() ?? [],
      secondaryMuscles: (json['secondaryMuscles'] as List<dynamic>?)?.map((e) => e.toString()).toList() ?? [],
      instructions: (json['instructions'] as List<dynamic>?)?.map((e) => e.toString()).toList() ?? [],
      imageUrls: (json['imageUrls'] as List<dynamic>?)?.map((e) => e.toString()).toList() ?? [],
      isFavorite: json['isFavorite'] == true,
    );
  }
}

class ExerciseFilters {
  final List<String> muscles;
  final List<String> equipment;
  final List<String> levels;
  final List<String> categories;

  ExerciseFilters({
    required this.muscles,
    required this.equipment,
    required this.levels,
    required this.categories,
  });

  factory ExerciseFilters.fromJson(Map<String, dynamic> json) {
    return ExerciseFilters(
      muscles: (json['muscles'] as List<dynamic>?)?.map((e) => e.toString()).toList() ?? [],
      equipment: (json['equipment'] as List<dynamic>?)?.map((e) => e.toString()).toList() ?? [],
      levels: (json['levels'] as List<dynamic>?)?.map((e) => e.toString()).toList() ?? [],
      categories: (json['categories'] as List<dynamic>?)?.map((e) => e.toString()).toList() ?? [],
    );
  }
}

class WorkoutEntry {
  final String id;
  final int position;
  final String? exerciseId;
  final String exerciseName;
  final String? thumbnailUrl;
  final int sets;
  final int? reps;
  final int? durationSeconds;
  final double? weightKg;

  WorkoutEntry({
    required this.id,
    required this.position,
    this.exerciseId,
    required this.exerciseName,
    this.thumbnailUrl,
    required this.sets,
    this.reps,
    this.durationSeconds,
    this.weightKg,
  });

  factory WorkoutEntry.fromJson(Map<String, dynamic> json) {
    return WorkoutEntry(
      id: json['id'] as String,
      position: (json['position'] as num).toInt(),
      exerciseId: json['exerciseId'] as String?,
      exerciseName: json['exerciseName'] as String,
      thumbnailUrl: json['thumbnailUrl'] as String?,
      sets: (json['sets'] as num).toInt(),
      reps: json['reps'] != null ? (json['reps'] as num).toInt() : null,
      durationSeconds: json['durationSeconds'] != null ? (json['durationSeconds'] as num).toInt() : null,
      weightKg: json['weightKg'] != null ? (json['weightKg'] as num).toDouble() : null,
    );
  }
}

class Workout {
  final String id;
  final String date;
  final String name;
  final int durationMin;
  final String intensity;
  final int? caloriesBurnedEst;
  final String? notes;
  final List<WorkoutEntry> entries;

  Workout({
    required this.id,
    required this.date,
    required this.name,
    required this.durationMin,
    required this.intensity,
    this.caloriesBurnedEst,
    this.notes,
    required this.entries,
  });

  factory Workout.fromJson(Map<String, dynamic> json) {
    return Workout(
      id: json['id'] as String,
      date: json['date'] as String,
      name: json['name'] as String,
      durationMin: (json['durationMin'] as num).toInt(),
      intensity: json['intensity'] as String,
      caloriesBurnedEst: json['caloriesBurnedEst'] != null ? (json['caloriesBurnedEst'] as num).toInt() : null,
      notes: json['notes'] as String?,
      entries: (json['entries'] as List<dynamic>?)
              ?.map((e) => WorkoutEntry.fromJson(e as Map<String, dynamic>))
              .toList() ??
          [],
    );
  }
}

class RangeDay {
  final String date;
  final int steps;
  final int waterMl;
  final int caloriesIn;
  final int caloriesBurnedEst;
  final int workoutCount;

  RangeDay({
    required this.date,
    required this.steps,
    required this.waterMl,
    required this.caloriesIn,
    required this.caloriesBurnedEst,
    required this.workoutCount,
  });

  factory RangeDay.fromJson(Map<String, dynamic> json) {
    return RangeDay(
      date: json['date'] as String,
      steps: (json['steps'] as num?)?.toInt() ?? 0,
      waterMl: (json['waterMl'] as num?)?.toInt() ?? 0,
      caloriesIn: (json['caloriesIn'] as num?)?.toInt() ?? 0,
      caloriesBurnedEst: (json['caloriesBurnedEst'] as num?)?.toInt() ?? 0,
      workoutCount: (json['workoutCount'] as num?)?.toInt() ?? 0,
    );
  }
}

class RangeAverages {
  final int steps;
  final int waterMl;
  final int caloriesIn;

  RangeAverages({
    required this.steps,
    required this.waterMl,
    required this.caloriesIn,
  });

  factory RangeAverages.fromJson(Map<String, dynamic> json) {
    return RangeAverages(
      steps: (json['steps'] as num?)?.toInt() ?? 0,
      waterMl: (json['waterMl'] as num?)?.toInt() ?? 0,
      caloriesIn: (json['caloriesIn'] as num?)?.toInt() ?? 0,
    );
  }
}

class RangeResponse {
  final List<RangeDay> days;
  final RangeAverages averages;

  RangeResponse({
    required this.days,
    required this.averages,
  });

  factory RangeResponse.fromJson(Map<String, dynamic> json) {
    return RangeResponse(
      days: (json['days'] as List<dynamic>?)
              ?.map((e) => RangeDay.fromJson(e as Map<String, dynamic>))
              .toList() ??
          [],
      averages: RangeAverages.fromJson(json['averages'] as Map<String, dynamic>),
    );
  }
}

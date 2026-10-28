class Insight {
  final String text;
  final String createdAt;
  final String model;
  final bool stale;

  Insight({
    required this.text,
    required this.createdAt,
    required this.model,
    required this.stale,
  });

  factory Insight.fromJson(Map<String, dynamic> json) {
    return Insight(
      text: json['text'] as String? ?? '',
      createdAt: json['createdAt'] as String? ?? '',
      model: json['model'] as String? ?? '',
      stale: json['stale'] as bool? ?? false,
    );
  }
}

class InsightResponse {
  final String kind;
  final String periodStart;
  final String periodEnd;
  final Map<String, dynamic> facts;
  final bool hasData;
  final bool aiEnabled;
  final bool aiConfigured;
  final int callsRemainingToday;
  final Insight? insight;

  InsightResponse({
    required this.kind,
    required this.periodStart,
    required this.periodEnd,
    required this.facts,
    required this.hasData,
    required this.aiEnabled,
    required this.aiConfigured,
    required this.callsRemainingToday,
    this.insight,
  });

  factory InsightResponse.fromJson(Map<String, dynamic> json) {
    return InsightResponse(
      kind: json['kind'] as String? ?? '',
      periodStart: json['periodStart'] as String? ?? '',
      periodEnd: json['periodEnd'] as String? ?? '',
      facts: json['facts'] != null ? Map<String, dynamic>.from(json['facts'] as Map) : {},
      hasData: json['hasData'] as bool? ?? false,
      aiEnabled: json['aiEnabled'] as bool? ?? true,
      aiConfigured: json['aiConfigured'] as bool? ?? false,
      callsRemainingToday: (json['callsRemainingToday'] as num?)?.toInt() ?? 0,
      insight: json['insight'] != null ? Insight.fromJson(json['insight'] as Map<String, dynamic>) : null,
    );
  }
}

class GenerateResponse {
  final Insight? insight;
  final String? fallbackReason;
  final int callsRemainingToday;

  GenerateResponse({
    this.insight,
    this.fallbackReason,
    required this.callsRemainingToday,
  });

  factory GenerateResponse.fromJson(Map<String, dynamic> json) {
    return GenerateResponse(
      insight: json['insight'] != null ? Insight.fromJson(json['insight'] as Map<String, dynamic>) : null,
      fallbackReason: json['fallbackReason'] as String?,
      callsRemainingToday: (json['callsRemainingToday'] as num?)?.toInt() ?? 0,
    );
  }
}

class MoodEntry {
  final String date;
  final int score;
  final String? note;

  MoodEntry({
    required this.date,
    required this.score,
    this.note,
  });

  factory MoodEntry.fromJson(Map<String, dynamic> json) {
    return MoodEntry(
      date: json['date'] as String? ?? '',
      score: (json['score'] as num?)?.toInt() ?? 3,
      note: json['note'] as String?,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'date': date,
      'score': score,
      if (note != null) 'note': note,
    };
  }
}

class MoodNudge {
  final bool show;
  final String? message;

  MoodNudge({
    required this.show,
    this.message,
  });

  factory MoodNudge.fromJson(Map<String, dynamic> json) {
    return MoodNudge(
      show: json['show'] as bool? ?? false,
      message: json['message'] as String?,
    );
  }
}

class AiSettings {
  final bool aiEnabled;
  final bool includeMood;

  AiSettings({
    required this.aiEnabled,
    required this.includeMood,
  });

  factory AiSettings.fromJson(Map<String, dynamic> json) {
    return AiSettings(
      aiEnabled: json['aiEnabled'] as bool? ?? true,
      includeMood: json['includeMood'] as bool? ?? false,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'aiEnabled': aiEnabled,
      'includeMood': includeMood,
    };
  }
}

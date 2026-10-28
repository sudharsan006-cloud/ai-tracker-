import '../core/api_client.dart';
import 'models.dart';

class FitnessRepository {
  final ApiClient apiClient;

  FitnessRepository(this.apiClient);

  Future<FitnessGoals> getGoals() async {
    final res = await apiClient.get('/fitness/goals');
    return FitnessGoals.fromJson(res as Map<String, dynamic>);
  }

  Future<FitnessGoals> putGoals({
    required int stepsGoal,
    required int waterGoalMl,
    int? calorieGoal,
  }) async {
    final res = await apiClient.put('/fitness/goals', {
      'stepsGoal': stepsGoal,
      'waterGoalMl': waterGoalMl,
      'calorieGoal': calorieGoal,
    });
    return FitnessGoals.fromJson(res as Map<String, dynamic>);
  }

  Future<Map<String, dynamic>> updateSteps({
    String? date,
    required int steps,
    required String source,
  }) async {
    final body = <String, dynamic>{
      'steps': steps,
      'source': source,
    };
    if (date != null) body['date'] = date;
    final res = await apiClient.put('/fitness/daily/steps', body);
    return res as Map<String, dynamic>;
  }

  Future<WaterEntry> addWater({required int amountMl, String? date}) async {
    final body = <String, dynamic>{'amountMl': amountMl};
    if (date != null) body['date'] = date;
    final res = await apiClient.post('/fitness/water', body);
    return WaterEntry.fromJson(res as Map<String, dynamic>);
  }

  Future<List<WaterEntry>> getWaterEntries({String? date}) async {
    final query = date != null ? '?date=$date' : '';
    final res = await apiClient.get('/fitness/water$query');
    return (res as List<dynamic>)
        .map((e) => WaterEntry.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<void> deleteWaterEntry(String id) async {
    await apiClient.delete('/fitness/water/$id');
  }

  Future<CalorieEntry> addCalorie({
    required int kcal,
    String? label,
    String? date,
  }) async {
    final body = <String, dynamic>{'kcal': kcal};
    if (label != null && label.isNotEmpty) body['label'] = label;
    if (date != null) body['date'] = date;
    final res = await apiClient.post('/fitness/calories', body);
    return CalorieEntry.fromJson(res as Map<String, dynamic>);
  }

  Future<List<CalorieEntry>> getCalorieEntries({String? date}) async {
    final query = date != null ? '?date=$date' : '';
    final res = await apiClient.get('/fitness/calories$query');
    return (res as List<dynamic>)
        .map((e) => CalorieEntry.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<void> deleteCalorieEntry(String id) async {
    await apiClient.delete('/fitness/calories/$id');
  }

  Future<DailySummary> getDailySummary({String? date}) async {
    final query = date != null ? '?date=$date' : '';
    final res = await apiClient.get('/fitness/daily$query');
    return DailySummary.fromJson(res as Map<String, dynamic>);
  }

  Future<RangeResponse> getRange({required String from, required String to}) async {
    final res = await apiClient.get('/fitness/range?from=$from&to=$to');
    return RangeResponse.fromJson(res as Map<String, dynamic>);
  }

  Future<Map<String, dynamic>> getExercises({
    String? q,
    String? muscle,
    String? equipment,
    String? level,
    String? category,
    bool? favoritesOnly,
    int page = 0,
    int size = 30,
  }) async {
    final params = <String>[];
    if (q != null && q.isNotEmpty) params.add('q=${Uri.encodeComponent(q)}');
    if (muscle != null && muscle.isNotEmpty) params.add('muscle=${Uri.encodeComponent(muscle)}');
    if (equipment != null && equipment.isNotEmpty) params.add('equipment=${Uri.encodeComponent(equipment)}');
    if (level != null && level.isNotEmpty) params.add('level=${Uri.encodeComponent(level)}');
    if (category != null && category.isNotEmpty) params.add('category=${Uri.encodeComponent(category)}');
    if (favoritesOnly == true) params.add('favoritesOnly=true');
    params.add('page=$page');
    params.add('size=$size');

    final query = params.isNotEmpty ? '?${params.join('&')}' : '';
    final res = await apiClient.get('/exercises$query') as Map<String, dynamic>;
    final items = (res['items'] as List<dynamic>)
        .map((e) => ExerciseSummary.fromJson(e as Map<String, dynamic>))
        .toList();

    return {
      'items': items,
      'page': res['page'],
      'size': res['size'],
      'total': res['total'],
    };
  }

  Future<ExerciseDetail> getExercise(String id) async {
    final res = await apiClient.get('/exercises/$id');
    return ExerciseDetail.fromJson(res as Map<String, dynamic>);
  }

  Future<ExerciseFilters> getExerciseFilters() async {
    final res = await apiClient.get('/exercises/filters');
    return ExerciseFilters.fromJson(res as Map<String, dynamic>);
  }

  Future<void> favoriteExercise(String id) async {
    await apiClient.post('/exercises/$id/favorite', {});
  }

  Future<void> unfavoriteExercise(String id) async {
    await apiClient.delete('/exercises/$id/favorite');
  }

  Future<Workout> createWorkout(Map<String, dynamic> body) async {
    final res = await apiClient.post('/workouts', body);
    return Workout.fromJson(res as Map<String, dynamic>);
  }

  Future<Map<String, dynamic>> getWorkouts({
    String? from,
    String? to,
    int page = 0,
    int size = 20,
  }) async {
    final params = <String>[];
    if (from != null && from.isNotEmpty) params.add('from=$from');
    if (to != null && to.isNotEmpty) params.add('to=$to');
    params.add('page=$page');
    params.add('size=$size');

    final query = '?${params.join('&')}';
    final res = await apiClient.get('/workouts$query') as Map<String, dynamic>;
    final items = (res['items'] as List<dynamic>)
        .map((e) => Workout.fromJson(e as Map<String, dynamic>))
        .toList();

    return {
      'items': items,
      'page': res['page'],
      'size': res['size'],
      'total': res['total'],
    };
  }

  Future<Workout> getWorkout(String id) async {
    final res = await apiClient.get('/workouts/$id');
    return Workout.fromJson(res as Map<String, dynamic>);
  }

  Future<Workout> updateWorkout(String id, Map<String, dynamic> body) async {
    final res = await apiClient.put('/workouts/$id', body);
    return Workout.fromJson(res as Map<String, dynamic>);
  }

  Future<void> deleteWorkout(String id) async {
    await apiClient.delete('/workouts/$id');
  }
}

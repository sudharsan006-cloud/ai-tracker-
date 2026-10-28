package com.lifetracker.exercise;

import com.lifetracker.common.NotFoundException;
import com.lifetracker.exercise.dto.ExerciseDetail;
import com.lifetracker.exercise.dto.ExerciseFiltersResponse;
import com.lifetracker.exercise.dto.ExercisePageResponse;
import com.lifetracker.exercise.dto.ExerciseSummary;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Array;
import java.util.*;

@Service
public class ExerciseService {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final ExerciseImageUrlBuilder imageUrlBuilder;

    public ExerciseService(JdbcTemplate jdbcTemplate, ExerciseImageUrlBuilder imageUrlBuilder) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.imageUrlBuilder = imageUrlBuilder;
    }

    public ExercisePageResponse getExercises(
            String q,
            String muscle,
            String equipment,
            String level,
            String category,
            Boolean favoritesOnly,
            int page,
            int size,
            UUID userId) {

        if (page < 0) page = 0;
        if (size <= 0) size = 30;
        if (size > 100) size = 100;

        StringBuilder whereClause = new StringBuilder(" WHERE 1=1 ");
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("userId", userId);

        if (q != null && !q.trim().isEmpty()) {
            String escaped = q.trim()
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
            whereClause.append(" AND e.name ILIKE ('%' || :q || '%') ");
            params.addValue("q", escaped);
        }

        if (muscle != null && !muscle.trim().isEmpty()) {
            whereClause.append(" AND :muscle = ANY(e.primary_muscles) ");
            params.addValue("muscle", muscle.trim());
        }

        if (equipment != null && !equipment.trim().isEmpty()) {
            whereClause.append(" AND e.equipment = :equipment ");
            params.addValue("equipment", equipment.trim());
        }

        if (level != null && !level.trim().isEmpty()) {
            whereClause.append(" AND e.level = :level ");
            params.addValue("level", level.trim());
        }

        if (category != null && !category.trim().isEmpty()) {
            whereClause.append(" AND e.category = :category ");
            params.addValue("category", category.trim());
        }

        if (Boolean.TRUE.equals(favoritesOnly)) {
            whereClause.append(" AND EXISTS (SELECT 1 FROM exercise_favorites f WHERE f.exercise_id = e.id AND f.user_id = :userId) ");
        }

        String countSql = "SELECT COUNT(*) FROM exercises e " + whereClause;
        Long total = namedParameterJdbcTemplate.queryForObject(countSql, params, Long.class);
        if (total == null) total = 0L;

        String selectSql = """
            SELECT e.id, e.name, e.level, e.equipment, e.category, e.primary_muscles, e.images,
                   (EXISTS (SELECT 1 FROM exercise_favorites f WHERE f.exercise_id = e.id AND f.user_id = :userId)) AS is_favorite
            FROM exercises e
        """ + whereClause + " ORDER BY e.name ASC LIMIT :limit OFFSET :offset ";

        params.addValue("limit", size);
        params.addValue("offset", page * size);

        List<ExerciseSummary> items = namedParameterJdbcTemplate.query(selectSql, params, (rs, rowNum) -> {
            UUID id = (UUID) rs.getObject("id");
            String name = rs.getString("name");
            String lvl = rs.getString("level");
            String equip = rs.getString("equipment");
            String cat = rs.getString("category");

            Array primaryArray = rs.getArray("primary_muscles");
            List<String> primaryMuscles = toList(primaryArray);

            Array imagesArray = rs.getArray("images");
            List<String> images = toList(imagesArray);
            String thumbnailUrl = imageUrlBuilder.thumbnailUrl(images);

            boolean isFavorite = rs.getBoolean("is_favorite");

            return new ExerciseSummary(id, name, lvl, equip, cat, primaryMuscles, thumbnailUrl, isFavorite);
        });

        return new ExercisePageResponse(items, page, size, total);
    }

    public ExerciseDetail getExercise(UUID id, UUID userId) {
        String sql = """
            SELECT e.id, e.name, e.level, e.equipment, e.category, e.force, e.mechanic,
                   e.primary_muscles, e.secondary_muscles, e.instructions, e.images,
                   (EXISTS (SELECT 1 FROM exercise_favorites f WHERE f.exercise_id = e.id AND f.user_id = ?)) AS is_favorite
            FROM exercises e
            WHERE e.id = ?
        """;

        try {
            return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                UUID exerciseId = (UUID) rs.getObject("id");
                String name = rs.getString("name");
                String level = rs.getString("level");
                String equipment = rs.getString("equipment");
                String category = rs.getString("category");
                String force = rs.getString("force");
                String mechanic = rs.getString("mechanic");

                List<String> primaryMuscles = toList(rs.getArray("primary_muscles"));
                List<String> secondaryMuscles = toList(rs.getArray("secondary_muscles"));
                List<String> instructions = toList(rs.getArray("instructions"));
                List<String> images = toList(rs.getArray("images"));
                List<String> imageUrls = imageUrlBuilder.imageUrls(images);

                boolean isFavorite = rs.getBoolean("is_favorite");

                return new ExerciseDetail(
                        exerciseId, name, level, equipment, category, force, mechanic,
                        primaryMuscles, secondaryMuscles, instructions, imageUrls, isFavorite
                );
            }, userId, id);
        } catch (EmptyResultDataAccessException e) {
            throw new NotFoundException("Exercise not found: " + id);
        }
    }

    public ExerciseFiltersResponse getFilters() {
        List<String> muscles = jdbcTemplate.queryForList(
                "SELECT DISTINCT unnest(primary_muscles) AS m FROM exercises ORDER BY m", String.class);

        List<String> equipment = jdbcTemplate.queryForList(
                "SELECT DISTINCT equipment FROM exercises WHERE equipment IS NOT NULL ORDER BY equipment", String.class);

        List<String> levels = jdbcTemplate.queryForList(
                "SELECT DISTINCT level FROM exercises WHERE level IS NOT NULL ORDER BY level", String.class);

        List<String> categories = jdbcTemplate.queryForList(
                "SELECT DISTINCT category FROM exercises WHERE category IS NOT NULL ORDER BY category", String.class);

        return new ExerciseFiltersResponse(muscles, equipment, levels, categories);
    }

    public void addFavorite(UUID id, UUID userId) {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM exercises WHERE id = ?", Integer.class, id);
        if (count == null || count == 0) {
            throw new NotFoundException("Exercise not found: " + id);
        }
        jdbcTemplate.update(
                "INSERT INTO exercise_favorites (user_id, exercise_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                userId, id
        );
    }

    public void removeFavorite(UUID id, UUID userId) {
        jdbcTemplate.update(
                "DELETE FROM exercise_favorites WHERE user_id = ? AND exercise_id = ?",
                userId, id
        );
    }

    private List<String> toList(Array array) {
        if (array == null) {
            return List.of();
        }
        try {
            Object obj = array.getArray();
            if (obj instanceof String[] arr) {
                return Arrays.asList(arr);
            } else if (obj instanceof Object[] arr) {
                List<String> list = new ArrayList<>();
                for (Object o : arr) {
                    if (o != null) list.add(o.toString());
                }
                return list;
            }
        } catch (Exception ignored) {
        }
        return List.of();
    }
}

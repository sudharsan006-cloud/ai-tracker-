package com.lifetracker.exercise;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.sql.PreparedStatement;
import java.util.List;

@Component
public class ExerciseSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ExerciseSeeder.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ExerciseSeeder(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedExercises();
    }

    public void seedExercises() {
        try {
            ClassPathResource resource = new ClassPathResource("seed/exercises.json");
            if (!resource.exists()) {
                log.warn("seed/exercises.json not found on classpath");
                return;
            }

            List<ExerciseSeedItem> items;
            try (InputStream is = resource.getInputStream()) {
                items = objectMapper.readValue(is, new TypeReference<List<ExerciseSeedItem>>() {});
            }

            Integer currentCount = jdbcTemplate.queryForObject("SELECT count(*) FROM exercises", Integer.class);
            if (currentCount != null && currentCount >= items.size()) {
                return;
            }

            String sql = """
                INSERT INTO exercises (
                    external_id, name, level, equipment, category, force, mechanic,
                    primary_muscles, secondary_muscles, instructions, images
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (external_id) DO NOTHING
            """;

            jdbcTemplate.batchUpdate(sql, items, 100, (PreparedStatement ps, ExerciseSeedItem item) -> {
                ps.setString(1, item.getId());
                ps.setString(2, item.getName());
                ps.setString(3, item.getLevel());
                ps.setString(4, item.getEquipment());
                ps.setString(5, item.getCategory());
                ps.setString(6, item.getForce());
                ps.setString(7, item.getMechanic());

                String[] primary = item.getPrimaryMuscles() != null ? item.getPrimaryMuscles().toArray(new String[0]) : new String[0];
                String[] secondary = item.getSecondaryMuscles() != null ? item.getSecondaryMuscles().toArray(new String[0]) : new String[0];
                String[] instructions = item.getInstructions() != null ? item.getInstructions().toArray(new String[0]) : new String[0];
                String[] images = item.getImages() != null ? item.getImages().toArray(new String[0]) : new String[0];

                ps.setArray(8, ps.getConnection().createArrayOf("text", primary));
                ps.setArray(9, ps.getConnection().createArrayOf("text", secondary));
                ps.setArray(10, ps.getConnection().createArrayOf("text", instructions));
                ps.setArray(11, ps.getConnection().createArrayOf("text", images));
            });

            log.info("Seeded exercises from seed/exercises.json successfully.");
        } catch (Exception e) {
            log.warn("Exercise seeding failed: {}", e.getMessage());
        }
    }
}

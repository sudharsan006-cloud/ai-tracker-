package com.lifetracker.exercise;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class ExerciseImageUrlBuilder {

    private final String baseUrl;

    public ExerciseImageUrlBuilder(@Value("${app.exercise.image-base-url:https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/}") String baseUrl) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            this.baseUrl = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/";
        } else if (!baseUrl.endsWith("/")) {
            this.baseUrl = baseUrl + "/";
        } else {
            this.baseUrl = baseUrl;
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public List<String> imageUrls(List<String> relativePaths) {
        if (relativePaths == null) {
            return List.of();
        }
        List<String> urls = new ArrayList<>();
        for (String path : relativePaths) {
            if (path == null || path.isBlank()) {
                continue;
            }
            String[] segments = path.split("/");
            List<String> encodedSegments = new ArrayList<>();
            for (String segment : segments) {
                encodedSegments.add(URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"));
            }
            urls.add(this.baseUrl + String.join("/", encodedSegments));
        }
        return urls;
    }

    public String thumbnailUrl(List<String> relativePaths) {
        List<String> urls = imageUrls(relativePaths);
        return urls.isEmpty() ? null : urls.get(0);
    }
}

package com.lifetracker.category.dto;

import com.lifetracker.category.Category;
import java.util.UUID;

public class CategoryResponse {
    private UUID id;
    private String name;
    private String type;
    private String icon;
    private Boolean isSystem;

    public CategoryResponse() {
    }

    public CategoryResponse(UUID id, String name, String type, String icon, Boolean isSystem) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.icon = icon;
        this.isSystem = isSystem;
    }

    public static CategoryResponse fromEntity(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getType(),
                category.getIcon(),
                category.isSystem()
        );
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Boolean getIsSystem() {
        return isSystem;
    }

    public void setIsSystem(Boolean isSystem) {
        this.isSystem = isSystem;
    }
}

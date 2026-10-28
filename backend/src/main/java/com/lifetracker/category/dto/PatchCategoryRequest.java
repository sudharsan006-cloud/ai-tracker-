package com.lifetracker.category.dto;

import jakarta.validation.constraints.Size;

public class PatchCategoryRequest {

    @Size(min = 1, max = 60, message = "Category name must be between 1 and 60 characters")
    private String name;

    @Size(max = 40, message = "Icon name cannot exceed 40 characters")
    private String icon;

    public PatchCategoryRequest() {
    }

    public PatchCategoryRequest(String name, String icon) {
        this.name = name;
        this.icon = icon;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }
}

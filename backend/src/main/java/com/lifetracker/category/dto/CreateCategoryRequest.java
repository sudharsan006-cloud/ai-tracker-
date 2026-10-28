package com.lifetracker.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CreateCategoryRequest {

    @NotBlank(message = "Category name is required")
    @Size(min = 1, max = 60, message = "Category name must be between 1 and 60 characters")
    private String name;

    @NotBlank(message = "Category type is required")
    @Pattern(regexp = "^(expense|income)$", message = "Type must be 'expense' or 'income'")
    private String type = "expense";

    @Size(max = 40, message = "Icon name cannot exceed 40 characters")
    private String icon;

    public CreateCategoryRequest() {
    }

    public CreateCategoryRequest(String name, String type, String icon) {
        this.name = name;
        this.type = type;
        this.icon = icon;
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
}

package com.lifetracker.category;

import com.lifetracker.category.dto.CategoryResponse;
import com.lifetracker.category.dto.CreateCategoryRequest;
import com.lifetracker.category.dto.PatchCategoryRequest;
import com.lifetracker.common.ConflictException;
import com.lifetracker.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public List<CategoryResponse> getCategories(UUID userId) {
        return categoryRepository.findVisibleCategories(userId).stream()
                .map(CategoryResponse::fromEntity)
                .toList();
    }

    @Transactional
    public CategoryResponse createCategory(UUID userId, CreateCategoryRequest request) {
        String trimmedName = request.getName().trim();
        if (categoryRepository.existsByNameIgnoreCaseAndVisibleToUser(trimmedName, userId)) {
            throw new ConflictException("Category with name '" + trimmedName + "' already exists");
        }

        Category category = new Category(userId, trimmedName, request.getType(), request.getIcon());
        category = categoryRepository.save(category);
        return CategoryResponse.fromEntity(category);
    }

    @Transactional
    public CategoryResponse patchCategory(UUID userId, UUID id, PatchCategoryRequest request) {
        Category category = categoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Category not found"));

        if (!category.isActive()) {
            throw new NotFoundException("Category not found");
        }

        if (request.getName() != null) {
            String trimmedName = request.getName().trim();
            if (trimmedName.isEmpty()) {
                throw new IllegalArgumentException("Category name cannot be empty");
            }
            if (categoryRepository.existsByNameIgnoreCaseAndVisibleToUserExcluding(trimmedName, userId, id)) {
                throw new ConflictException("Category with name '" + trimmedName + "' already exists");
            }
            category.setName(trimmedName);
        }

        if (request.getIcon() != null) {
            category.setIcon(request.getIcon());
        }

        category = categoryRepository.save(category);
        return CategoryResponse.fromEntity(category);
    }

    @Transactional
    public void deleteCategory(UUID userId, UUID id) {
        Category category = categoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Category not found"));

        if (!category.isActive()) {
            throw new NotFoundException("Category not found");
        }

        category.setActive(false);
        categoryRepository.save(category);
    }
}

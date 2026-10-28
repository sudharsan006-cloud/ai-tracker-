package com.lifetracker.user.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;

public class UpdateProfileRequest {

    private String displayName;

    @Past(message = "dateOfBirth must be in the past")
    private LocalDate dateOfBirth;

    @Pattern(regexp = "^(male|female|other)$", message = "sexAtBirth must be male, female, or other")
    private String sexAtBirth;

    @DecimalMin(value = "50.0", message = "heightCm must be between 50 and 250")
    @DecimalMax(value = "250.0", message = "heightCm must be between 50 and 250")
    private BigDecimal heightCm;

    @DecimalMin(value = "20.0", message = "weightKg must be between 20 and 400")
    @DecimalMax(value = "400.0", message = "weightKg must be between 20 and 400")
    private BigDecimal weightKg;

    private String timezone;

    public UpdateProfileRequest() {
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getSexAtBirth() {
        return sexAtBirth;
    }

    public void setSexAtBirth(String sexAtBirth) {
        this.sexAtBirth = sexAtBirth;
    }

    public BigDecimal getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(BigDecimal heightCm) {
        this.heightCm = heightCm;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }
}

package com.lifetracker.user.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ProfileResponse {

    private String email;
    private String displayName;
    private LocalDate dateOfBirth;
    private String sexAtBirth;
    private BigDecimal heightCm;
    private BigDecimal weightKg;
    private String timezone;

    public ProfileResponse() {
    }

    public ProfileResponse(String email, String displayName, LocalDate dateOfBirth, String sexAtBirth, BigDecimal heightCm, BigDecimal weightKg, String timezone) {
        this.email = email;
        this.displayName = displayName;
        this.dateOfBirth = dateOfBirth;
        this.sexAtBirth = sexAtBirth;
        this.heightCm = heightCm;
        this.weightKg = weightKg;
        this.timezone = timezone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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

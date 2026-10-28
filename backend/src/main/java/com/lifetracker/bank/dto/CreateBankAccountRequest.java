package com.lifetracker.bank.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class CreateBankAccountRequest {

    @NotNull(message = "bankId is required")
    private Integer bankId;

    @NotNull(message = "currencyId is required")
    private Integer currencyId;

    @Pattern(regexp = "^[0-9]{4}$", message = "accountLast4 must be exactly 4 digits")
    private String accountLast4;

    private String nickname;

    @Pattern(regexp = "^(savings|current|credit)$", message = "accountType must be savings, current, or credit")
    private String accountType;

    public CreateBankAccountRequest() {
    }

    public Integer getBankId() {
        return bankId;
    }

    public void setBankId(Integer bankId) {
        this.bankId = bankId;
    }

    public Integer getCurrencyId() {
        return currencyId;
    }

    public void setCurrencyId(Integer currencyId) {
        this.currencyId = currencyId;
    }

    public String getAccountLast4() {
        return accountLast4;
    }

    public void setAccountLast4(String accountLast4) {
        this.accountLast4 = accountLast4;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }
}

package com.lifetracker.bank.dto;

import com.lifetracker.bank.BankAccount;

import java.util.UUID;

public class BankAccountResponse {

    private UUID id;
    private Integer bankId;
    private Integer currencyId;
    private String accountLast4;
    private String nickname;
    private String accountType;
    private Boolean isActive;

    public BankAccountResponse() {
    }

    public BankAccountResponse(UUID id, Integer bankId, Integer currencyId, String accountLast4, String nickname, String accountType, Boolean isActive) {
        this.id = id;
        this.bankId = bankId;
        this.currencyId = currencyId;
        this.accountLast4 = accountLast4;
        this.nickname = nickname;
        this.accountType = accountType;
        this.isActive = isActive;
    }

    public static BankAccountResponse fromEntity(BankAccount account) {
        return new BankAccountResponse(
                account.getId(),
                account.getBankId(),
                account.getCurrencyId(),
                account.getAccountLast4(),
                account.getNickname(),
                account.getAccountType(),
                account.isActive()
        );
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }
}

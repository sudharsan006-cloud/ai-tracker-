package com.lifetracker.bank;

import com.lifetracker.bank.dto.BankAccountResponse;
import com.lifetracker.bank.dto.CreateBankAccountRequest;
import com.lifetracker.bank.dto.PatchBankAccountRequest;
import com.lifetracker.common.LimitReachedException;
import com.lifetracker.common.NotFoundException;
import com.lifetracker.config.ConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class BankService {

    private final BankRepository bankRepository;
    private final CurrencyRepository currencyRepository;
    private final BankAccountRepository bankAccountRepository;
    private final ConfigService configService;

    public BankService(
            BankRepository bankRepository,
            CurrencyRepository currencyRepository,
            BankAccountRepository bankAccountRepository,
            ConfigService configService) {
        this.bankRepository = bankRepository;
        this.currencyRepository = currencyRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.configService = configService;
    }

    public List<Bank> getActiveBanks() {
        return bankRepository.findByIsActiveTrue();
    }

    public List<Currency> getCurrencies() {
        return currencyRepository.findAll();
    }

    public List<BankAccountResponse> getUserBankAccounts(UUID userId) {
        return bankAccountRepository.findByUserIdAndIsActiveTrue(userId)
                .stream()
                .map(BankAccountResponse::fromEntity)
                .toList();
    }

    @Transactional
    public BankAccountResponse createBankAccount(UUID userId, CreateBankAccountRequest request) {
        // Validate Bank
        Optional<Bank> bankOpt = bankRepository.findById(request.getBankId());
        if (bankOpt.isEmpty() || !bankOpt.get().isActive()) {
            throw new IllegalArgumentException("Invalid or inactive bankId: " + request.getBankId());
        }

        // Validate Currency
        if (!currencyRepository.existsById(request.getCurrencyId())) {
            throw new IllegalArgumentException("Invalid currencyId: " + request.getCurrencyId());
        }

        // Check account limit
        BigDecimal cap = configService.get("bank_account_max_per_user");
        long currentCount = bankAccountRepository.countByUserIdAndIsActiveTrue(userId);
        if (currentCount >= cap.longValue()) {
            throw new LimitReachedException("Bank account limit reached (" + cap + " accounts max)");
        }

        BankAccount account = new BankAccount(
                userId,
                request.getBankId(),
                request.getCurrencyId(),
                request.getAccountLast4(),
                request.getNickname(),
                request.getAccountType()
        );

        account = bankAccountRepository.save(account);
        return BankAccountResponse.fromEntity(account);
    }

    @Transactional
    public BankAccountResponse patchBankAccount(UUID userId, UUID accountId, PatchBankAccountRequest request) {
        BankAccount account = bankAccountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new NotFoundException("Bank account not found"));

        if (!account.isActive()) {
            throw new NotFoundException("Bank account not found");
        }

        if (request.getNickname() != null) {
            account.setNickname(request.getNickname());
        }
        if (request.getAccountLast4() != null) {
            account.setAccountLast4(request.getAccountLast4());
        }
        if (request.getAccountType() != null) {
            account.setAccountType(request.getAccountType());
        }

        account = bankAccountRepository.save(account);
        return BankAccountResponse.fromEntity(account);
    }

    @Transactional
    public void deleteBankAccount(UUID userId, UUID accountId) {
        BankAccount account = bankAccountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new NotFoundException("Bank account not found"));

        if (!account.isActive()) {
            throw new NotFoundException("Bank account not found");
        }

        account.setActive(false);
        bankAccountRepository.save(account);
    }
}

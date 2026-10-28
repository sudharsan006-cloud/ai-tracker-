package com.lifetracker;

import com.lifetracker.bank.BankAccount;
import com.lifetracker.bank.BankAccountRepository;
import com.lifetracker.bank.CurrencyRepository;
import com.lifetracker.budget.Budget;
import com.lifetracker.budget.BudgetRepository;
import com.lifetracker.budget.BudgetService;
import com.lifetracker.budget.dto.BudgetResponse;
import com.lifetracker.budget.dto.CreateBudgetRequest;
import com.lifetracker.category.Category;
import com.lifetracker.category.CategoryRepository;
import com.lifetracker.category.CategoryService;
import com.lifetracker.category.dto.CreateCategoryRequest;
import com.lifetracker.common.ConflictException;
import com.lifetracker.common.NotFoundException;
import com.lifetracker.sms.UnparsedSmsQueueRepository;
import com.lifetracker.transaction.BankTransaction;
import com.lifetracker.transaction.BankTransactionRepository;
import com.lifetracker.transaction.TransactionMatchingService;
import com.lifetracker.transaction.TransactionService;
import com.lifetracker.transaction.dto.CreateTransactionRequest;
import com.lifetracker.transaction.dto.PatchTransactionRequest;
import com.lifetracker.transaction.dto.TransactionResponse;
import com.lifetracker.user.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class Phase2ServiceTest {

    private CategoryRepository categoryRepository;
    private CategoryService categoryService;

    private BankTransactionRepository transactionRepository;
    private BankAccountRepository bankAccountRepository;
    private UserProfileRepository userProfileRepository;
    private UnparsedSmsQueueRepository unparsedSmsQueueRepository;
    private TransactionMatchingService matchingService;
    private JdbcTemplate jdbcTemplate;
    private TransactionService transactionService;

    private BudgetRepository budgetRepository;
    private CurrencyRepository currencyRepository;
    private BudgetService budgetService;

    @BeforeEach
    void setUp() {
        categoryRepository = mock(CategoryRepository.class);
        categoryService = new CategoryService(categoryRepository);

        transactionRepository = mock(BankTransactionRepository.class);
        bankAccountRepository = mock(BankAccountRepository.class);
        userProfileRepository = mock(UserProfileRepository.class);
        unparsedSmsQueueRepository = mock(UnparsedSmsQueueRepository.class);
        matchingService = mock(TransactionMatchingService.class);
        jdbcTemplate = mock(JdbcTemplate.class);

        transactionService = new TransactionService(
                transactionRepository,
                bankAccountRepository,
                categoryRepository,
                userProfileRepository,
                unparsedSmsQueueRepository,
                matchingService,
                jdbcTemplate
        );

        budgetRepository = mock(BudgetRepository.class);
        currencyRepository = mock(CurrencyRepository.class);

        budgetService = new BudgetService(
                budgetRepository,
                currencyRepository,
                categoryRepository,
                userProfileRepository,
                jdbcTemplate
        );
    }

    @Test
    @DisplayName("Test 15: Categories duplicate name with system category -> 409; deleting category used by transaction keeps transaction")
    void testCategoriesDuplicateAndSoftDelete() {
        UUID userId = UUID.randomUUID();

        // System category with same name exists
        when(categoryRepository.existsByNameIgnoreCaseAndVisibleToUser("Food & Dining", userId)).thenReturn(true);

        CreateCategoryRequest req = new CreateCategoryRequest("Food & Dining", "expense", "icon");
        assertThatThrownBy(() -> categoryService.createCategory(userId, req))
                .isInstanceOf(ConflictException.class);

        // Soft delete category
        UUID catId = UUID.randomUUID();
        Category cat = new Category(userId, "Custom Cat", "expense", "icon");
        cat.setId(catId);
        when(categoryRepository.findByIdAndUserId(catId, userId)).thenReturn(Optional.of(cat));

        categoryService.deleteCategory(userId, catId);
        assertThat(cat.isActive()).isFalse();
        verify(categoryRepository).save(cat);
    }

    @Test
    @DisplayName("Test 2 & 16: Manual transaction validation: currency from account; amount 0 or negative -> 400; account of another user -> 400/404")
    void testManualTransactionValidation() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        BankAccount account = new BankAccount(userA, 1, 1, "1234", "My Acc", "checking");
        account.setId(accountId);

        when(bankAccountRepository.findByIdAndUserId(accountId, userA)).thenReturn(Optional.of(account));
        when(bankAccountRepository.findByIdAndUserId(accountId, userB)).thenReturn(Optional.empty());

        // Amount <= 0 -> 400
        CreateTransactionRequest reqZero = new CreateTransactionRequest();
        reqZero.setBankAccountId(accountId);
        reqZero.setAmount(BigDecimal.ZERO);
        reqZero.setDirection("debit");
        assertThatThrownBy(() -> transactionService.createTransaction(userA, reqZero, "manual", null))
                .isInstanceOf(IllegalArgumentException.class);

        // Account of another user -> 400
        CreateTransactionRequest reqOther = new CreateTransactionRequest();
        reqOther.setBankAccountId(accountId);
        reqOther.setAmount(new BigDecimal("100.00"));
        reqOther.setDirection("debit");
        assertThatThrownBy(() -> transactionService.createTransaction(userB, reqOther, "manual", null))
                .isInstanceOf(IllegalArgumentException.class);

        // Valid transaction copies currency from account
        when(transactionRepository.save(any(BankTransaction.class))).thenAnswer(i -> {
            BankTransaction tx = i.getArgument(0);
            tx.setId(UUID.randomUUID());
            return tx;
        });

        TransactionResponse resp = transactionService.createTransaction(userA, reqOther, "manual", null);
        assertThat(resp.getCurrencyId()).isEqualTo(account.getCurrencyId());
    }

    @Test
    @DisplayName("Test 9: Linked transaction PATCH of amount -> 409; PATCH of categoryId -> 200")
    void testPatchLinkedTransaction() {
        UUID userId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();

        BankTransaction tx = new BankTransaction();
        tx.setId(txId);
        tx.setUserId(userId);
        tx.setSelfTransfer(true);
        tx.setSelfTransferMatchedId(UUID.randomUUID());
        tx.setAmount(new BigDecimal("500.00"));

        when(transactionRepository.findByIdAndUserId(txId, userId)).thenReturn(Optional.of(tx));

        // Attempting to patch amount on linked tx -> 409
        PatchTransactionRequest patchAmount = new PatchTransactionRequest();
        patchAmount.setAmount(new BigDecimal("600.00"));
        assertThatThrownBy(() -> transactionService.patchTransaction(userId, txId, patchAmount))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Unlink or resolve the match first");

        // Patching category on linked tx -> 200
        UUID newCatId = UUID.randomUUID();
        Category cat = new Category(userId, "New Cat", "expense", null);
        when(categoryRepository.findById(newCatId)).thenReturn(Optional.of(cat));
        when(transactionRepository.save(any(BankTransaction.class))).thenReturn(tx);

        PatchTransactionRequest patchCat = new PatchTransactionRequest();
        patchCat.setCategoryId(newCatId);

        TransactionResponse patched = transactionService.patchTransaction(userId, txId, patchCat);
        assertThat(patched).isNotNull();
        assertThat(tx.getCategoryId()).isEqualTo(newCatId);
    }

    @Test
    @DisplayName("Test 7 & 8: Duplicate confirm deletes transaction; deleting linked self-transfer resets partner")
    void testConfirmDuplicateAndDeleteLinkedSelfTransfer() {
        UUID userId = UUID.randomUUID();
        UUID txId1 = UUID.randomUUID();
        UUID txId2 = UUID.randomUUID();

        BankTransaction tx1 = new BankTransaction();
        tx1.setId(txId1);
        tx1.setUserId(userId);
        tx1.setReviewStatus("possible_duplicate");
        tx1.setReviewPartnerId(txId2);

        BankTransaction tx2 = new BankTransaction();
        tx2.setId(txId2);
        tx2.setUserId(userId);
        tx2.setReviewStatus("possible_duplicate");
        tx2.setReviewPartnerId(txId1);

        when(transactionRepository.findByIdAndUserId(txId1, userId)).thenReturn(Optional.of(tx1));
        when(transactionRepository.findByIdAndUserId(txId2, userId)).thenReturn(Optional.of(tx2));

        // Confirming duplicate on tx1 deletes tx1 and clears partner flag via delete steps
        transactionService.confirmReview(userId, txId1);
        verify(transactionRepository).delete(tx1);
        verify(transactionRepository).clearReviewFlagsForPartner(txId1);

        // Deleting linked self-transfer resets partner
        UUID st1 = UUID.randomUUID();
        UUID st2 = UUID.randomUUID();
        BankTransaction selfTx1 = new BankTransaction();
        selfTx1.setId(st1);
        selfTx1.setUserId(userId);
        selfTx1.setSelfTransfer(true);
        selfTx1.setSelfTransferMatchedId(st2);

        BankTransaction selfTx2 = new BankTransaction();
        selfTx2.setId(st2);
        selfTx2.setUserId(userId);
        selfTx2.setSelfTransfer(true);
        selfTx2.setSelfTransferMatchedId(st1);

        when(transactionRepository.findByIdAndUserId(st1, userId)).thenReturn(Optional.of(selfTx1));
        when(transactionRepository.findById(st2)).thenReturn(Optional.of(selfTx2));

        transactionService.deleteTransaction(userId, st1);
        assertThat(selfTx2.isSelfTransfer()).isFalse();
        assertThat(selfTx2.getSelfTransferMatchedId()).isNull();
        verify(transactionRepository).delete(selfTx1);
    }

    @Test
    @DisplayName("Test 5 & 6: Self-transfer confirm and refund confirm")
    void testConfirmSelfTransferAndRefund() {
        UUID userId = UUID.randomUUID();

        // 1. Self transfer confirm
        UUID st1 = UUID.randomUUID();
        UUID st2 = UUID.randomUUID();
        BankTransaction tx1 = new BankTransaction();
        tx1.setId(st1);
        tx1.setUserId(userId);
        tx1.setReviewStatus("possible_self_transfer");
        tx1.setReviewPartnerId(st2);

        BankTransaction tx2 = new BankTransaction();
        tx2.setId(st2);
        tx2.setUserId(userId);
        tx2.setReviewStatus("possible_self_transfer");
        tx2.setReviewPartnerId(st1);

        Category transferCat = new Category(null, "Transfer", "expense", "swap_horiz");
        transferCat.setId(UUID.randomUUID());

        when(transactionRepository.findByIdAndUserId(st1, userId)).thenReturn(Optional.of(tx1));
        when(transactionRepository.findByIdAndUserId(st2, userId)).thenReturn(Optional.of(tx2));
        when(categoryRepository.findFirstByUserIdIsNullAndNameIgnoreCase("Transfer")).thenReturn(Optional.of(transferCat));

        transactionService.confirmReview(userId, st1);
        assertThat(tx1.isSelfTransfer()).isTrue();
        assertThat(tx1.getSelfTransferMatchedId()).isEqualTo(st2);
        assertThat(tx1.getCategoryId()).isEqualTo(transferCat.getId());
        assertThat(tx2.isSelfTransfer()).isTrue();
        assertThat(tx2.getSelfTransferMatchedId()).isEqualTo(st1);
        assertThat(tx2.getCategoryId()).isEqualTo(transferCat.getId());

        // 2. Refund confirm
        UUID creditId = UUID.randomUUID();
        UUID debitId = UUID.randomUUID();
        BankTransaction creditTx = new BankTransaction();
        creditTx.setId(creditId);
        creditTx.setUserId(userId);
        creditTx.setReviewStatus("possible_refund_match");
        creditTx.setReviewPartnerId(debitId);

        BankTransaction debitTx = new BankTransaction();
        debitTx.setId(debitId);
        debitTx.setUserId(userId);

        when(transactionRepository.findByIdAndUserId(creditId, userId)).thenReturn(Optional.of(creditTx));
        when(transactionRepository.findByIdAndUserId(debitId, userId)).thenReturn(Optional.of(debitTx));

        transactionService.confirmReview(userId, creditId);
        assertThat(creditTx.getRefundMatchedId()).isEqualTo(debitId);
        assertThat(creditTx.getReviewStatus()).isEqualTo("none");
        assertThat(debitTx.getRefundMatchedId()).isEqualTo(creditId);
    }

    @Test
    @DisplayName("Test 14: Unlink clears both sides; unlink on a plain transaction -> 409")
    void testUnlinkTransaction() {
        UUID userId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();

        // Plain transaction with nothing to unlink
        BankTransaction plain = new BankTransaction();
        plain.setId(txId);
        plain.setUserId(userId);
        when(transactionRepository.findByIdAndUserId(txId, userId)).thenReturn(Optional.of(plain));

        assertThatThrownBy(() -> transactionService.unlinkTransaction(userId, txId))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Nothing to unlink");

        // Linked refund
        UUID debitId = UUID.randomUUID();
        plain.setRefundMatchedId(debitId);
        BankTransaction debit = new BankTransaction();
        debit.setId(debitId);
        debit.setRefundMatchedId(txId);

        when(transactionRepository.findById(debitId)).thenReturn(Optional.of(debit));

        transactionService.unlinkTransaction(userId, txId);
        assertThat(plain.getRefundMatchedId()).isNull();
        assertThat(debit.getRefundMatchedId()).isNull();
    }

    @Test
    @DisplayName("Test 10 & 11: Budget duplicate -> 409; budget status calculation")
    void testBudgetDuplicateAndStatus() {
        UUID userId = UUID.randomUUID();
        UUID catId = UUID.randomUUID();

        when(currencyRepository.existsById(1)).thenReturn(true);
        Category expenseCat = new Category(userId, "Groceries", "expense", null);
        when(categoryRepository.findById(catId)).thenReturn(Optional.of(expenseCat));

        when(budgetRepository.existsByUserIdAndCategoryIdAndCurrencyIdAndIsActiveTrue(userId, catId, 1))
                .thenReturn(true);

        CreateBudgetRequest req = new CreateBudgetRequest(catId, 1, new BigDecimal("1000.00"));
        assertThatThrownBy(() -> budgetService.createBudget(userId, req))
                .isInstanceOf(ConflictException.class);

        // Budget status
        Budget budget = new Budget(userId, catId, 1, new BigDecimal("1000.00"));
        budget.setId(UUID.randomUUID());

        when(budgetRepository.findByUserIdAndIsActiveTrue(userId)).thenReturn(java.util.List.of(budget));
        when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), eq(userId), eq(1), any(), any(), eq(catId), eq(catId)))
                .thenReturn(new BigDecimal("1200.00")); // spent 1200 > 1000

        var budgets = budgetService.getBudgets(userId);
        assertThat(budgets).hasSize(1);
        BudgetResponse bResp = budgets.get(0);
        assertThat(bResp.getSpent()).isEqualByComparingTo("1200.00");
        assertThat(bResp.getRemaining()).isEqualByComparingTo("-200.00");
        assertThat(bResp.getPercentUsed()).isEqualByComparingTo("120.0");
        assertThat(bResp.isOverBudget()).isTrue();
    }
}

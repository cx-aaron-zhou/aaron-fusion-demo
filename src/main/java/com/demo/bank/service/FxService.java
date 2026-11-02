package com.demo.bank.service;

import com.demo.bank.model.Account;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cross-currency transfers between accounts, with a flat fee and a daily send limit.
 * Rates are quoted in parts-per-million of the destination currency per unit of source.
 */
public class FxService {

    private static final long FEE_BPS = 50;                 // 0.50%
    private static final long DAILY_LIMIT_CENTS = 500_000;  // $5,000.00 per source account
    private static final long PPM = 1_000_000L;

    private static final Map<String, Long> RATES = Map.of(
            "USD>EUR", 920_000L,
            "EUR>USD", 1_100_000L);

    private final AccountService accounts;
    private final Map<String, Long> sentToday = new ConcurrentHashMap<>();

    public FxService(AccountService accounts) {
        this.accounts = accounts;
    }

    /** @return the amount credited to the destination account, in its own currency. */
    public long transfer(String fromAccountId, String toAccountId, long amountCents) {
        Account from = accounts.getAccount(fromAccountId);
        Account to = accounts.getAccount(toAccountId);
        if (from == null || to == null) {
            throw new IllegalArgumentException("account not found");
        }
        if (amountCents <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (from.getBalanceCents() < amountCents) {
            throw new IllegalArgumentException("insufficient funds");
        }
        String day = fromAccountId + "@" + LocalDate.now();
        long used = sentToday.getOrDefault(day, 0L);
        if (used + amountCents > DAILY_LIMIT_CENTS) {
            throw new IllegalArgumentException("daily limit exceeded");
        }

        long fee = amountCents * FEE_BPS / 10_000;
        long credited = amountCents * rate(from.getCurrency(), to.getCurrency()) / PPM;

        from.setBalanceCents(from.getBalanceCents() - amountCents - fee);
        to.setBalanceCents(to.getBalanceCents() + credited);
        sentToday.merge(day, amountCents, Long::sum);
        return credited;
    }

    private static long rate(String fromCurrency, String toCurrency) {
        if (fromCurrency.equals(toCurrency)) {
            return PPM;
        }
        return RATES.getOrDefault(fromCurrency + ">" + toCurrency, PPM);
    }
}

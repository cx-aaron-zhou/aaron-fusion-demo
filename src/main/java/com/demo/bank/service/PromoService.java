package com.demo.bank.service;

import com.demo.bank.model.Account;
import com.demo.bank.model.Txn;
import com.demo.bank.model.User;
import com.demo.bank.store.DataStore;
import com.demo.bank.util.Ids;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Promotional credits. Each code can be redeemed once per customer.
 */
public class PromoService {

    private static final Map<String, Long> CODES = Map.of(
            "WELCOME10", 1_000L,
            "SPRING5", 500L);

    private final DataStore store;
    private final Set<String> redeemed = ConcurrentHashMap.newKeySet();

    public PromoService(DataStore store) {
        this.store = store;
    }

    public long redeem(User caller, String code, String accountId) {
        Long bonus = code == null ? null : CODES.get(code.toUpperCase());
        if (bonus == null) {
            throw new IllegalArgumentException("invalid code");
        }
        Account account = store.getAccountById(accountId);
        if (account == null || !account.getOwnerUserId().equals(caller.getId())) {
            throw new IllegalArgumentException("not your account");
        }
        String key = caller.getId() + ":" + code;
        if (redeemed.contains(key)) {
            throw new IllegalArgumentException("code already redeemed");
        }
        account.setBalanceCents(account.getBalanceCents() + bonus);
        store.addTxn(new Txn(Ids.random(6), "promo", accountId, bonus, "promo " + code));
        redeemed.add(key);
        return account.getBalanceCents();
    }
}

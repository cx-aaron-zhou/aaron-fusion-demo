package com.demo.bank.store;

import com.demo.bank.model.Account;
import com.demo.bank.model.User;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import com.demo.bank.model.Txn;

/**
 * In-memory data store. Everything lives in concurrent maps so the demo needs no
 * database and SCA has no persistence driver to scan.
 *
 * <p>Seed users get randomly generated demo passwords (never printed to stdout) so
 * there are no credential literals committed to source. Override via the
 * {@code DEMO_ADMIN_PW}, {@code DEMO_ALICE_PW}, and {@code DEMO_BOB_PW}
 * environment variables.
 */
public class DataStore {

    private static final SecureRandom RNG = new SecureRandom();

    private final Map<String, User> usersById = new ConcurrentHashMap<>();
    private final Map<String, User> usersByUsername = new ConcurrentHashMap<>();
    private final Map<String, Account> accountsById = new ConcurrentHashMap<>();
    private final List<Txn> txns = new CopyOnWriteArrayList<>();

    public DataStore() {
        seed();
    }

    private void seed() {
        String adminPw = envOrRandom("DEMO_ADMIN_PW");
        String alicePw = envOrRandom("DEMO_ALICE_PW");
        String bobPw = envOrRandom("DEMO_BOB_PW");

        User admin = new User("u-admin", "admin", adminPw, "admin");
        admin.setEmail("admin@demo.local");
        User alice = new User("u-alice", "alice", alicePw, "customer");
        alice.setEmail("alice@demo.local");
        User bob = new User("u-bob", "bob", bobPw, "customer");
        bob.setEmail("bob@demo.local");

        putUser(admin);
        putUser(alice);
        putUser(bob);

        putAccount(new Account("acc-admin", "u-admin", 0));
        putAccount(new Account("acc-alice", "u-alice", 100_000)); // $1,000.00
        putAccount(new Account("acc-bob", "u-bob", 50_000));      // $500.00
        putAccount(new Account("acc-bob-eur", "u-bob", 20_000, "EUR"));
        putAccount(new Account("acc-acme-ops", "u-alice", 250_000));       // Acme operating account
        putAccount(new Account("acc-globex-treasury", "u-admin", 900_000)); // Globex treasury

        // Credentials are intentionally not logged to prevent CWE-497 server
        // information exposure. Set DEMO_ADMIN_PW / DEMO_ALICE_PW / DEMO_BOB_PW
        // environment variables to supply known passwords, or retrieve them
        // securely through the application's own authentication endpoints.
        System.out.println("=== demo credentials initialised (passwords suppressed) ===");
    }

    private static String envOrRandom(String envVar) {
        String fromEnv = System.getenv(envVar);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        byte[] buf = new byte[9];
        RNG.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    public void putUser(User user) {
        usersById.put(user.getId(), user);
        usersByUsername.put(user.getUsername(), user);
    }

    public User getUserById(String id) {
        return usersById.get(id);
    }

    public User getUserByUsername(String username) {
        return usersByUsername.get(username);
    }

    public void putAccount(Account account) {
        accountsById.put(account.getId(), account);
    }

    public Account getAccountById(String id) {
        return accountsById.get(id);
    }

    public Collection<Account> allAccounts() {
        return accountsById.values();
    }

    public void addTxn(Txn txn) {
        txns.add(txn);
    }

    public List<Txn> txnsFor(String accountId) {
        return txns.stream()
                .filter(t -> t.getFromAccountId().equals(accountId) || t.getToAccountId().equals(accountId))
                .toList();
    }

    public Collection<User> allUsers() {
        return usersById.values();
    }
}

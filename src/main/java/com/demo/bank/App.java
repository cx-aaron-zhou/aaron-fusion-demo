package com.demo.bank;

import com.demo.bank.handler.ApiHandler;
import com.demo.bank.handler.AssistantHandler;
import com.demo.bank.handler.BulkHandler;
import com.demo.bank.handler.FxHandler;
import com.demo.bank.handler.LoanHandler;
import com.demo.bank.handler.NotificationHandler;
import com.demo.bank.handler.OrgHandler;
import com.demo.bank.handler.PaymentsHandler;
import com.demo.bank.handler.PromoHandler;
import com.demo.bank.handler.RecoveryHandler;
import com.demo.bank.service.AccountService;
import com.demo.bank.service.AssistantService;
import com.demo.bank.service.AuthService;
import com.demo.bank.service.FxService;
import com.demo.bank.service.LoanService;
import com.demo.bank.service.NotificationService;
import com.demo.bank.service.OrgService;
import com.demo.bank.service.PaymentLinkService;
import com.demo.bank.service.PromoService;
import com.demo.bank.service.RecoveryService;
import com.demo.bank.service.StatementTokenService;
import com.demo.bank.store.DataStore;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Entry point. Starts a JDK HttpServer exposing the demo banking API on port 8080
 * (override with the PORT environment variable).
 */
public class App {

    public static void main(String[] args) throws IOException {
        int port = parsePort(System.getenv("PORT"), 8080);

        DataStore store = new DataStore();
        AuthService auth = new AuthService(store);
        NotificationService notifications = new NotificationService(store);
        AccountService accounts = new AccountService(store, notifications);
        OrgService orgs = new OrgService(store);
        FxService fx = new FxService(accounts);
        LoanService loans = new LoanService(store);
        RecoveryService recovery = new RecoveryService(store);
        PromoService promos = new PromoService(store);
        PaymentLinkService links = new PaymentLinkService(accounts);
        StatementTokenService statements = new StatementTokenService();
        AssistantService assistant = new AssistantService(store, accounts);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api", new ApiHandler(auth, accounts, store));
        server.createContext("/api/orgs", new OrgHandler(auth, orgs, accounts, store));
        server.createContext("/api/fx", new FxHandler(auth, fx, accounts));
        server.createContext("/api/loans", new LoanHandler(auth, loans));
        server.createContext("/api/recovery", new RecoveryHandler(auth, recovery));
        server.createContext("/api/promo", new PromoHandler(auth, promos));
        server.createContext("/api/payments", new PaymentsHandler(auth, links, statements, accounts, store));
        server.createContext("/api/bulk", new BulkHandler(auth, accounts));
        server.createContext("/api/assistant", new AssistantHandler(auth, assistant));
        server.createContext("/api/notifications", new NotificationHandler(auth, notifications));
        server.setExecutor(Executors.newFixedThreadPool(16));
        server.start();

        ScheduledExecutorService jobs = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "notification-job");
            t.setDaemon(true);
            return t;
        });
        jobs.scheduleWithFixedDelay(notifications::drain, 1, 1, TimeUnit.SECONDS);

        System.out.println("aaron-demo-bank listening on http://localhost:" + port);
    }

    private static int parsePort(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            System.err.println("Invalid PORT value '" + value.trim() + "': " + e.getMessage() + ". Using default: " + fallback);
            return fallback;
        }
    }
}

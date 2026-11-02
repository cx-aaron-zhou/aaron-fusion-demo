package com.demo.bank.handler;

import com.demo.bank.model.User;
import com.demo.bank.service.AuthService;
import com.demo.bank.service.NotificationService;
import com.demo.bank.util.Http;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.List;

/** GET /api/notifications  (Bearer token) - the caller's inbox. */
public class NotificationHandler extends BaseHandler {
    private final NotificationService notifications;

    public NotificationHandler(AuthService auth, NotificationService notifications) {
        super(auth);
        this.notifications = notifications;
    }

    @Override
    protected void route(HttpExchange ex, String method, String[] seg) throws IOException {
        if (!method.equals("GET") || seg.length != 3) {
            notFound(ex);
            return;
        }
        User caller = caller(ex);
        if (caller == null) {
            return;
        }
        List<String> inbox = notifications.inboxFor(caller.getId());
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < inbox.size(); i++) {
            sb.append(i == 0 ? "" : ",").append('"').append(Http.escape(inbox.get(i))).append('"');
        }
        Http.sendJson(ex, 200, sb.append("]").toString());
    }
}

package com.demo.bank.handler;

import com.demo.bank.model.User;
import com.demo.bank.service.AssistantService;
import com.demo.bank.service.AuthService;
import com.demo.bank.util.Http;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.List;

/** POST /api/assistant/ask  question   (Bearer token) */
public class AssistantHandler extends BaseHandler {
    private final AssistantService assistant;

    public AssistantHandler(AuthService auth, AssistantService assistant) {
        super(auth);
        this.assistant = assistant;
    }

    @Override
    protected void route(HttpExchange ex, String method, String[] seg) throws IOException {
        if (!(method.equals("POST") && seg.length == 4 && seg[3].equals("ask"))) {
            notFound(ex);
            return;
        }
        User caller = caller(ex);
        if (caller == null) {
            return;
        }
        List<String> actions = assistant.ask(caller, Http.parseForm(ex).getOrDefault("question", ""));
        StringBuilder sb = new StringBuilder("{\"actions\":[");
        for (int i = 0; i < actions.size(); i++) {
            sb.append(i == 0 ? "" : ",").append('"').append(Http.escape(actions.get(i))).append('"');
        }
        Http.sendJson(ex, 200, sb.append("]}").toString());
    }
}

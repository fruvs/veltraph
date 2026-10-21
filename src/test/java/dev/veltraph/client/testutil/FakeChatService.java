package dev.veltraph.client.testutil;

import dev.veltraph.client.service.ChatService;

import java.util.ArrayList;
import java.util.List;

/** Records chat messages for assertions. */
public final class FakeChatService implements ChatService {

    public final List<String> messages = new ArrayList<>();

    @Override
    public void send(String message) {
        messages.add(message);
    }

    public String last() {
        return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }
}

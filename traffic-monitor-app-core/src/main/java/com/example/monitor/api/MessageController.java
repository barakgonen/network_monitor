package com.example.monitor.api;

import com.example.monitor.model.ObservedMessageUi;
import com.example.monitor.store.RecentMessageStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class MessageController {
    private final RecentMessageStore recentMessageStore;

    public MessageController(RecentMessageStore recentMessageStore) {
        this.recentMessageStore = recentMessageStore;
    }

    @GetMapping("/api/messages/recent")
    public List<ObservedMessageUi> recentMessages() {
        return recentMessageStore.recent().stream().map(ObservedMessageUi::from).toList();
    }
}

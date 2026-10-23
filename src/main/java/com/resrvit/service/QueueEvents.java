package com.resrvit.service;

import com.resrvit.domain.Lab;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

/** Pushes live updates over STOMP once the surrounding transaction has committed. */
@Component
@RequiredArgsConstructor
public class QueueEvents {

    private final SimpMessagingTemplate broker;

    public void labChanged(Lab lab) {
        if (lab == null) return;
        afterCommit(() -> broker.convertAndSend("/topic/queue." + lab.getId(), Map.of("type", "queue-changed")));
    }

    public void conversationMessage(Long conversationId, Map<String, Object> payload) {
        afterCommit(() -> broker.convertAndSend("/topic/conversation." + conversationId, payload));
    }

    public void inboxChanged(Lab lab) {
        if (lab == null) return;
        afterCommit(() -> broker.convertAndSend("/topic/inbox." + lab.getId(), Map.of("type", "inbox-changed")));
    }

    /** Tells one user's open pages to refresh their counters ("notifications" or "chat"). */
    public void userChanged(Long userId, String what) {
        if (userId == null) return;
        afterCommit(() -> broker.convertAndSend("/topic/user." + userId, Map.of("type", what)));
    }

    private void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    r.run();
                }
            });
        } else {
            r.run();
        }
    }
}

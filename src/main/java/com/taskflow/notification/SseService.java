package com.taskflow.notification;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * In-memory registry of Server-Sent Events connections. A single instance is
 * enough here; for horizontal scaling, back it with Redis pub/sub.
 */
@Component
public class SseService {

    private static final long TIMEOUT_MILLIS = 30 * 60 * 1000L;

    private final Map<UUID, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        emitters.computeIfAbsent(userId, key -> new CopyOnWriteArraySet<>()).add(emitter);
        Runnable cleanup = () -> remove(userId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());
        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("status", "connected")));
        } catch (IOException ex) {
            cleanup.run();
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    public void publish(UUID userId, Object payload) {
        Set<SseEmitter> connections = emitters.get(userId);
        if (connections == null || connections.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : connections) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(payload));
            } catch (IOException | IllegalStateException ex) {
                remove(userId, emitter);
                emitter.complete();
            }
        }
    }

    /** Keeps proxies and load balancers from closing idle streams. */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        emitters.forEach((userId, connections) -> {
            for (SseEmitter emitter : connections) {
                try {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                } catch (IOException | IllegalStateException ex) {
                    remove(userId, emitter);
                    emitter.complete();
                }
            }
        });
    }

    public int connectionCount(UUID userId) {
        Set<SseEmitter> connections = emitters.get(userId);
        return connections == null ? 0 : connections.size();
    }

    private void remove(UUID userId, SseEmitter emitter) {
        Set<SseEmitter> connections = emitters.get(userId);
        if (connections != null) {
            connections.remove(emitter);
            if (connections.isEmpty()) {
                emitters.remove(userId, connections);
            }
        }
    }
}

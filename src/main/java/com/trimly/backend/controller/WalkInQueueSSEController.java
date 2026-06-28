package com.trimly.backend.controller;

import com.trimly.backend.service.WalkInQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/shops/{shopId}/walk-in-queue/{entryId}/position")
@RequiredArgsConstructor
public class WalkInQueueSSEController {

    private final WalkInQueueService walkInQueueService;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamQueuePosition(
            @PathVariable UUID shopId,
            @PathVariable UUID entryId
    ) {
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);

        scheduler.scheduleAtFixedRate(() -> {
            try {
                WalkInQueueService.QueuePositionSnapshot snapshot =
                        walkInQueueService.getQueuePosition(shopId, entryId);

                emitter.send(SseEmitter.event()
                        .name("queue-position")
                        .data(snapshot));

                switch (snapshot.status()) {
                    case COMPLETED, CANCELLED, NO_SHOW -> emitter.complete();
                    default -> {}
                }

            } catch (IOException e) {
                emitter.completeWithError(e);
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        }, 0, 10, TimeUnit.SECONDS);

        emitter.onCompletion(() -> {});
        emitter.onTimeout(emitter::complete);

        return emitter;
    }
}
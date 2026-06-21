package com.trimly.backend.controller;

import com.trimly.backend.dto.walkin.WalkInJoinRequest;
import com.trimly.backend.dto.walkin.WalkInQueueEntryResponse;
import com.trimly.backend.dto.walkin.WalkInStartRequest;
import com.trimly.backend.security.CustomUserDetails;
import com.trimly.backend.service.WalkInQueueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/shops/{shopId}/walk-in-queue")
@RequiredArgsConstructor
public class WalkInQueueController {

    private final WalkInQueueService walkInQueueService;

    @PostMapping
    public ResponseEntity<WalkInQueueEntryResponse> joinQueue(
            @PathVariable UUID shopId,
            @Valid @RequestBody WalkInJoinRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        WalkInQueueEntryResponse response = walkInQueueService.joinQueue(shopId, request, userDetails.getUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<WalkInQueueEntryResponse>> listQueue(
            @PathVariable UUID shopId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        List<WalkInQueueEntryResponse> response = walkInQueueService.listQueue(shopId, userDetails.getUser().getId());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{entryId}/start")
    public ResponseEntity<WalkInQueueEntryResponse> startQueueEntry(
            @PathVariable UUID shopId,
            @PathVariable UUID entryId,
            @Valid @RequestBody WalkInStartRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        WalkInQueueEntryResponse response = walkInQueueService.startQueueEntry(shopId, entryId, request, userDetails.getUser().getId());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{entryId}/complete")
    public ResponseEntity<WalkInQueueEntryResponse> completeQueueEntry(
            @PathVariable UUID shopId,
            @PathVariable UUID entryId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        WalkInQueueEntryResponse response = walkInQueueService.completeQueueEntry(shopId, entryId, userDetails.getUser().getId());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{entryId}/cancel")
    public ResponseEntity<WalkInQueueEntryResponse> cancelQueueEntry(
            @PathVariable UUID shopId,
            @PathVariable UUID entryId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        WalkInQueueEntryResponse response = walkInQueueService.cancelQueueEntry(shopId, entryId, userDetails.getUser().getId());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{entryId}/no-show")
    public ResponseEntity<WalkInQueueEntryResponse> markNoShow(
            @PathVariable UUID shopId,
            @PathVariable UUID entryId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        WalkInQueueEntryResponse response = walkInQueueService.markNoShow(shopId, entryId, userDetails.getUser().getId());
        return ResponseEntity.ok(response);
    }

}
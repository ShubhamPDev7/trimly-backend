package com.trimly.backend.controller;

import com.trimly.backend.dto.bill.BillRequest;
import com.trimly.backend.dto.bill.BillResponse;
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
import org.springframework.data.domain.Page;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shops/{shopId}/walk-in-queue")
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


    @PostMapping("/{entryId}/bill")
    public ResponseEntity<BillResponse> createWalkInBill(
            @PathVariable UUID shopId,
            @PathVariable UUID entryId,
            @Valid @RequestBody BillRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        BillResponse response = walkInQueueService.createWalkInBill(shopId, entryId, request, userDetails.getUser().getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }


    @GetMapping("/history")
    public ResponseEntity<Page<WalkInQueueEntryResponse>> getQueueHistory(
            @PathVariable UUID shopId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(
                walkInQueueService.getQueueHistory(shopId, userDetails.getUser().getId(), page, size));
    }

}
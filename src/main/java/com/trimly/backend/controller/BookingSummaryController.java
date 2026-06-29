package com.trimly.backend.controller;

import com.trimly.backend.dto.booking.BookingSummaryResponse;
import com.trimly.backend.service.BookingSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class BookingSummaryController {

    private final BookingSummaryService bookingSummaryService;

    @GetMapping("/api/v1/shops/{shopId}/booking-summary")
    public ResponseEntity<BookingSummaryResponse> getBookingSummary(
            @PathVariable UUID shopId,
            @RequestParam UUID serviceId,
            @RequestParam UUID staffId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(bookingSummaryService.getSummary(shopId, serviceId, staffId, date));
    }
}
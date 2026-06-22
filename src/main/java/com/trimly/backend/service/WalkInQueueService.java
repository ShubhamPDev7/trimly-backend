package com.trimly.backend.service;

import com.trimly.backend.dto.walkin.WalkInJoinRequest;
import com.trimly.backend.dto.walkin.WalkInQueueEntryResponse;
import com.trimly.backend.dto.walkin.WalkInQueueServiceResponse;
import com.trimly.backend.dto.walkin.WalkInStartRequest;
import com.trimly.backend.entity.Booking;
import com.trimly.backend.entity.BookingServiceItem;
import com.trimly.backend.entity.ServiceItem;
import com.trimly.backend.entity.ShopStaff;
import com.trimly.backend.entity.User;
import com.trimly.backend.entity.WalkInQueueEntry;
import com.trimly.backend.entity.WalkInQueueServiceItem;
import com.trimly.backend.enums.BookingStatus;
import com.trimly.backend.enums.WalkInStatus;
import com.trimly.backend.exception.ResourceNotFoundException;
import com.trimly.backend.repository.BookingRepository;
import com.trimly.backend.repository.BookingServiceItemRepository;
import com.trimly.backend.repository.ServiceItemRepository;
import com.trimly.backend.repository.ShopStaffRepository;
import com.trimly.backend.repository.WalkInQueueEntryRepository;
import com.trimly.backend.repository.WalkInQueueServiceItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WalkInQueueService {

    private final WalkInQueueEntryRepository walkInQueueEntryRepository;
    private final WalkInQueueServiceItemRepository walkInQueueServiceItemRepository;
    private final BookingRepository bookingRepository;
    private final BookingServiceItemRepository bookingServiceItemRepository;
    private final ServiceItemRepository serviceItemRepository;
    private final ShopStaffRepository shopStaffRepository;
    private final ShopAccessService shopAccessService;
    private final ShopHoursService shopHoursService;

    @Transactional
    public WalkInQueueEntryResponse joinQueue(UUID shopId, WalkInJoinRequest request, User currentUser) {
        boolean isStaff = shopAccessService.hasShopAccess(currentUser.getId(), shopId);

        UUID customerId;
        String guestName;
        String guestPhone;

        if (isStaff) {
            if (request.guestName() == null || request.guestName().isBlank()
                    || request.guestPhone() == null || request.guestPhone().isBlank()) {
                throw new IllegalArgumentException(
                        "guestName and guestPhone are required when staff adds a walk-in on behalf of a customer.");
            }
            customerId = null;
            guestName = request.guestName();
            guestPhone = request.guestPhone();
        } else {
            customerId = currentUser.getId();
            guestName = null;
            guestPhone = null;
        }

        List<ServiceItem> services = serviceItemRepository.findAllById(request.serviceIds());

        if (services.size() != request.serviceIds().size()) {
            throw new IllegalArgumentException("One or more services were not found.");
        }

        for (ServiceItem service : services) {
            if (!service.getShopId().equals(shopId)) {
                throw new IllegalArgumentException("One or more services do not belong to this shop.");
            }
        }

        if (request.preferredStaffId() != null) {
            boolean staffExists = shopAccessService.hasShopAccess(request.preferredStaffId(), shopId);
            if (!staffExists) {
                throw new IllegalArgumentException("The specified preferred staff member does not belong to this shop.");
            }
        }

        WalkInQueueEntry entry = WalkInQueueEntry.builder()
                .shopId(shopId)
                .customerId(customerId)
                .guestName(guestName)
                .guestPhone(guestPhone)
                .preferredStaffId(request.preferredStaffId())
                .status(WalkInStatus.WAITING)
                .build();

        WalkInQueueEntry savedEntry = walkInQueueEntryRepository.save(entry);

        List<WalkInQueueServiceItem> queueServiceItems = services.stream()
                .map(service -> WalkInQueueServiceItem.builder()
                        .queueEntryId(savedEntry.getId())
                        .serviceId(service.getId())
                        .priceAtJoin(service.getPrice())
                        .build())
                .collect(Collectors.toList());

        walkInQueueServiceItemRepository.saveAll(queueServiceItems);

        Map<UUID, WaitEstimate> estimates = calculateWaitEstimates(shopId);
        List<WalkInQueueEntry> waitingEntries = walkInQueueEntryRepository
                .findByShopIdAndStatusOrderByJoinedAtAsc(shopId, WalkInStatus.WAITING);
        int position = 0;
        for (WalkInQueueEntry e : waitingEntries) {
            position++;
            if (e.getId().equals(savedEntry.getId())) break;
        }
        return toResponse(savedEntry, estimates.get(savedEntry.getId()), position);
    }

    public List<WalkInQueueEntryResponse> listQueue(UUID shopId, UUID currentUserId) {
        shopAccessService.verifyShopAccess(currentUserId, shopId);

        List<WalkInQueueEntry> entries = walkInQueueEntryRepository.findByShopIdAndStatusInOrderByJoinedAtAsc(
                shopId, List.of(WalkInStatus.WAITING, WalkInStatus.IN_PROGRESS));

        Map<UUID, WaitEstimate> estimates = calculateWaitEstimates(shopId);

        int position = 0;
        List<WalkInQueueEntryResponse> responses = new ArrayList<>();

        for (WalkInQueueEntry entry : entries) {
            if (entry.getStatus() == WalkInStatus.WAITING) {
                position++;
                responses.add(toResponse(entry, estimates.get(entry.getId()), position));
            } else {
                responses.add(toResponse(entry, null, null));
            }
        }

        return responses;
    }

    @Transactional
    public WalkInQueueEntryResponse startQueueEntry(UUID shopId, UUID entryId, WalkInStartRequest request, UUID currentUserId) {
        shopAccessService.verifyShopAccess(currentUserId, shopId);

        boolean staffExists = shopAccessService.hasShopAccess(request.staffId(), shopId);
        if (!staffExists) {
            throw new IllegalArgumentException("The specified staff member does not belong to this shop.");
        }

        WalkInQueueEntry entry = getEntryOrThrow(shopId, entryId);

        if (entry.getStatus() != WalkInStatus.WAITING) {
            throw new IllegalArgumentException("Only a waiting queue entry can be started.");
        }

        entry.setAssignedStaffId(request.staffId());
        entry.setStatus(WalkInStatus.IN_PROGRESS);
        entry.setStartedAt(Instant.now());

        WalkInQueueEntry updated = walkInQueueEntryRepository.save(entry);

        return toResponse(updated, null, null);
    }

    @Transactional
    public WalkInQueueEntryResponse completeQueueEntry(UUID shopId, UUID entryId, UUID currentUserId) {
        shopAccessService.verifyShopAccess(currentUserId, shopId);

        WalkInQueueEntry entry = getEntryOrThrow(shopId, entryId);

        if (entry.getStatus() != WalkInStatus.IN_PROGRESS) {
            throw new IllegalArgumentException("Only an in-progress queue entry can be completed.");
        }

        entry.setStatus(WalkInStatus.COMPLETED);
        entry.setCompletedAt(Instant.now());

        WalkInQueueEntry updated = walkInQueueEntryRepository.save(entry);

        return toResponse(updated, null, null);
    }

    @Transactional
    public WalkInQueueEntryResponse cancelQueueEntry(UUID shopId, UUID entryId, UUID currentUserId) {
        shopAccessService.verifyShopAccess(currentUserId, shopId);

        WalkInQueueEntry entry = getEntryOrThrow(shopId, entryId);

        if (entry.getStatus() != WalkInStatus.WAITING && entry.getStatus() != WalkInStatus.IN_PROGRESS) {
            throw new IllegalArgumentException("Only a waiting or in-progress queue entry can be cancelled.");
        }

        entry.setStatus(WalkInStatus.CANCELLED);

        WalkInQueueEntry updated = walkInQueueEntryRepository.save(entry);

        return toResponse(updated, null, null);
    }

    @Transactional
    public WalkInQueueEntryResponse markNoShow(UUID shopId, UUID entryId, UUID currentUserId) {
        shopAccessService.verifyShopAccess(currentUserId, shopId);

        WalkInQueueEntry entry = getEntryOrThrow(shopId, entryId);

        if (entry.getStatus() != WalkInStatus.WAITING) {
            throw new IllegalArgumentException("Only a waiting queue entry can be marked as a no-show.");
        }

        entry.setStatus(WalkInStatus.NO_SHOW);

        WalkInQueueEntry updated = walkInQueueEntryRepository.save(entry);

        return toResponse(updated, null, null);
    }

    private WalkInQueueEntry getEntryOrThrow(UUID shopId, UUID entryId) {
        WalkInQueueEntry entry = walkInQueueEntryRepository.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("Queue entry not found."));

        if (!entry.getShopId().equals(shopId)) {
            throw new ResourceNotFoundException("Queue entry not found.");
        }

        return entry;
    }

    private WalkInQueueEntryResponse toResponseWithEstimate(WalkInQueueEntry entry, Map<UUID, WaitEstimate> estimates) {
        return toResponse(entry, estimates.get(entry.getId()), null);
    }

    private WalkInQueueEntryResponse toResponse(WalkInQueueEntry entry, WaitEstimate estimate, Integer position) {
        List<WalkInQueueServiceItem> items = walkInQueueServiceItemRepository.findByQueueEntryId(entry.getId());
        List<UUID> serviceIds = items.stream().map(WalkInQueueServiceItem::getServiceId).collect(Collectors.toList());

        Map<UUID, ServiceItem> servicesById = serviceItemRepository.findAllById(serviceIds).stream()
                .collect(Collectors.toMap(ServiceItem::getId, s -> s));

        List<WalkInQueueServiceResponse> serviceResponses = items.stream()
                .map(item -> {
                    ServiceItem service = servicesById.get(item.getServiceId());
                    return new WalkInQueueServiceResponse(
                            item.getServiceId(),
                            service != null ? service.getName() : "Unknown",
                            item.getPriceAtJoin()
                    );
                })
                .collect(Collectors.toList());

        return new WalkInQueueEntryResponse(
                entry.getId(),
                entry.getShopId(),
                entry.getCustomerId(),
                entry.getGuestName(),
                entry.getGuestPhone(),
                entry.getPreferredStaffId(),
                entry.getAssignedStaffId(),
                entry.getStatus(),
                serviceResponses,
                entry.getJoinedAt(),
                entry.getStartedAt(),
                entry.getCompletedAt(),
                estimate != null ? estimate.estimatedWaitMinutes() : null,
                estimate != null ? estimate.estimatedStartAt() : null,
                estimate != null ? estimate.likelyStaffId() : null,
                position
        );
    }

    private record Interval(Instant start, Instant end) {
    }

    private record WaitEstimate(UUID queueEntryId, Instant estimatedStartAt, long estimatedWaitMinutes, UUID likelyStaffId) {
    }

    private Map<UUID, WaitEstimate> calculateWaitEstimates(UUID shopId) {
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        List<UUID> staffIds = shopStaffRepository.findByShopId(shopId).stream()
                .map(ShopStaff::getUserId)
                .collect(Collectors.toList());

        Map<UUID, List<Interval>> timelines = buildInitialTimelines(shopId, staffIds, today, now);

        List<WalkInQueueEntry> waitingEntries = walkInQueueEntryRepository
                .findByShopIdAndStatusOrderByJoinedAtAsc(shopId, WalkInStatus.WAITING);

        Map<UUID, WaitEstimate> results = new HashMap<>();

        for (WalkInQueueEntry entry : waitingEntries) {
            long durationMinutes = getQueueEntryDurationMinutes(entry.getId());

            if (entry.getPreferredStaffId() != null) {
                UUID staffId = entry.getPreferredStaffId();
                List<Interval> timeline = timelines.computeIfAbsent(staffId, k -> new ArrayList<>());

                Instant start = findEarliestSlot(shopId, timeline, now, durationMinutes);
                insertInterval(timeline, start, start.plusSeconds(durationMinutes * 60));

                long waitMinutes = Duration.between(now, start).toMinutes();
                results.put(entry.getId(), new WaitEstimate(entry.getId(), start, Math.max(waitMinutes, 0), staffId));

            } else {
                UUID bestStaffId = null;
                Instant bestStart = null;

                for (UUID staffId : staffIds) {
                    List<Interval> timeline = timelines.computeIfAbsent(staffId, k -> new ArrayList<>());
                    Instant start = findEarliestSlot(shopId, timeline, now, durationMinutes);

                    if (bestStart == null || start.isBefore(bestStart)) {
                        bestStart = start;
                        bestStaffId = staffId;
                    }
                }

                if (bestStaffId != null) {
                    List<Interval> timeline = timelines.get(bestStaffId);
                    insertInterval(timeline, bestStart, bestStart.plusSeconds(durationMinutes * 60));

                    long waitMinutes = Duration.between(now, bestStart).toMinutes();
                    results.put(entry.getId(), new WaitEstimate(entry.getId(), bestStart, Math.max(waitMinutes, 0), bestStaffId));
                }
            }
        }

        return results;
    }

    private Map<UUID, List<Interval>> buildInitialTimelines(UUID shopId, List<UUID> staffIds, LocalDate today, Instant now) {
        Map<UUID, List<Interval>> timelines = new HashMap<>();
        for (UUID staffId : staffIds) {
            timelines.put(staffId, new ArrayList<>());
        }

        List<WalkInQueueEntry> inProgress = walkInQueueEntryRepository
                .findByShopIdAndStatusOrderByJoinedAtAsc(shopId, WalkInStatus.IN_PROGRESS);

        for (WalkInQueueEntry entry : inProgress) {
            if (entry.getAssignedStaffId() == null || entry.getStartedAt() == null) {
                continue;
            }
            long durationMinutes = getQueueEntryDurationMinutes(entry.getId());
            Instant end = entry.getStartedAt().plusSeconds(durationMinutes * 60);

            if (end.isBefore(now)) {
                end = now;
            }

            timelines.computeIfAbsent(entry.getAssignedStaffId(), k -> new ArrayList<>())
                    .add(new Interval(entry.getStartedAt(), end));
        }

        List<Booking> todaysBookings = bookingRepository.findByShopIdAndBookingDateBetween(shopId, today, today);

        for (Booking booking : todaysBookings) {
            if (booking.getStaffId() == null) {
                continue;
            }
            if (booking.getStatus() == BookingStatus.REJECTED || booking.getStatus() == BookingStatus.CANCELLED) {
                continue;
            }

            long durationMinutes = getBookingDurationMinutes(booking.getId());
            if (durationMinutes <= 0) {
                continue;
            }

            Instant start = booking.getBookingDate().atTime(booking.getTimeSlot()).toInstant(ZoneOffset.UTC);
            Instant end = start.plusSeconds(durationMinutes * 60);

            if (end.isBefore(now)) {
                continue;
            }

            timelines.computeIfAbsent(booking.getStaffId(), k -> new ArrayList<>())
                    .add(new Interval(start, end));
        }

        for (List<Interval> timeline : timelines.values()) {
            timeline.sort((a, b) -> a.start().compareTo(b.start()));
        }

        return timelines;
    }

    private Instant findEarliestSlot(UUID shopId, List<Interval> sortedIntervals, Instant from, long durationMinutes) {
        Instant cursor = shopHoursService.nextOpenInstant(shopId, from);

        for (int dayAttempts = 0; dayAttempts < 14; dayAttempts++) {
            LocalDate cursorDate = cursor.atZone(ZoneOffset.UTC).toLocalDate();
            Instant closingTime = shopHoursService.closingInstant(shopId, cursorDate);

            if (closingTime == null) {
                cursor = shopHoursService.nextOpenInstant(shopId, cursor.plusSeconds(60));
                continue;
            }

            for (Interval busy : sortedIntervals) {
                if (busy.start().isAfter(cursor)) {
                    long gapMinutes = Duration.between(cursor, busy.start()).toMinutes();
                    if (gapMinutes >= durationMinutes) {
                        Instant slotEnd = cursor.plusSeconds(durationMinutes * 60);
                        if (!slotEnd.isAfter(closingTime)) {
                            return cursor;
                        }
                    }
                }
                if (busy.end().isAfter(cursor)) {
                    cursor = busy.end();
                }
            }


            Instant slotEnd = cursor.plusSeconds(durationMinutes * 60);
            if (!slotEnd.isAfter(closingTime)) {
                return cursor;
            }

            cursor = shopHoursService.nextOpenInstant(shopId, closingTime.plusSeconds(60));
        }

        return cursor;
    }

    private void insertInterval(List<Interval> timeline, Instant start, Instant end) {
        timeline.add(new Interval(start, end));
        timeline.sort((a, b) -> a.start().compareTo(b.start()));
    }

    private long getQueueEntryDurationMinutes(UUID queueEntryId) {
        List<WalkInQueueServiceItem> items = walkInQueueServiceItemRepository.findByQueueEntryId(queueEntryId);
        List<UUID> serviceIds = items.stream().map(WalkInQueueServiceItem::getServiceId).collect(Collectors.toList());
        List<ServiceItem> services = serviceItemRepository.findAllById(serviceIds);
        return services.stream()
                .mapToLong(s -> s.getEstTimeMinutes() != null ? s.getEstTimeMinutes() : 0)
                .sum();
    }

    private long getBookingDurationMinutes(UUID bookingId) {
        List<BookingServiceItem> items = bookingServiceItemRepository.findByBookingId(bookingId);
        List<UUID> serviceIds = items.stream().map(BookingServiceItem::getServiceId).collect(Collectors.toList());
        List<ServiceItem> services = serviceItemRepository.findAllById(serviceIds);
        return services.stream()
                .mapToLong(s -> s.getEstTimeMinutes() != null ? s.getEstTimeMinutes() : 0)
                .sum();
    }

}
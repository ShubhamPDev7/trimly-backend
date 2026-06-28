package com.trimly.backend.service;

import com.trimly.backend.dto.servicerecord.ServiceRecordRequest;
import com.trimly.backend.dto.servicerecord.ServiceRecordResponse;
import com.trimly.backend.entity.Booking;
import com.trimly.backend.entity.ServiceRecord;
import com.trimly.backend.entity.User;
import com.trimly.backend.entity.WalkInQueueEntry;
import com.trimly.backend.enums.BookingStatus;
import com.trimly.backend.enums.WalkInStatus;
import com.trimly.backend.exception.ResourceNotFoundException;
import com.trimly.backend.exception.ShopAccessDeniedException;
import com.trimly.backend.repository.BookingRepository;
import com.trimly.backend.repository.ServiceRecordRepository;
import com.trimly.backend.repository.WalkInQueueEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ServiceRecordService {

    private final ServiceRecordRepository serviceRecordRepository;
    private final BookingRepository bookingRepository;
    private final WalkInQueueEntryRepository walkInQueueEntryRepository;
    private final ShopAccessService shopAccessService;

    @Transactional
    public ServiceRecordResponse createForBooking(UUID shopId, UUID bookingId,
                                                  ServiceRecordRequest request, User currentUser) {
        shopAccessService.verifyShopAccess(currentUser.getId(), shopId);

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found."));

        if (!booking.getShopId().equals(shopId)) {
            throw new ShopAccessDeniedException("Booking does not belong to this shop.");
        }
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new IllegalStateException("Service record can only be created for a completed booking.");
        }
        if (serviceRecordRepository.findByBookingId(bookingId).isPresent()) {
            throw new IllegalStateException("A service record already exists for this booking.");
        }

        ServiceRecord record = ServiceRecord.builder()
                .shopId(shopId)
                .staffId(currentUser.getId())
                .customerId(booking.getCustomerId())
                .bookingId(bookingId)
                .notes(request.notes())
                .productsUsed(request.productsUsed())
                .photoUrls(request.photoUrls())
                .build();

        return toResponse(serviceRecordRepository.save(record));
    }

    @Transactional
    public ServiceRecordResponse createForWalkIn(UUID shopId, UUID entryId,
                                                 ServiceRecordRequest request, User currentUser) {
        shopAccessService.verifyShopAccess(currentUser.getId(), shopId);

        WalkInQueueEntry entry = walkInQueueEntryRepository.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("Walk-in queue entry not found."));

        if (!entry.getShopId().equals(shopId)) {
            throw new ShopAccessDeniedException("Walk-in entry does not belong to this shop.");
        }
        if (entry.getStatus() != WalkInStatus.COMPLETED) {
            throw new IllegalStateException("Service record can only be created for a completed walk-in.");
        }
        if (serviceRecordRepository.findByWalkInQueueEntryId(entryId).isPresent()) {
            throw new IllegalStateException("A service record already exists for this walk-in entry.");
        }

        ServiceRecord record = ServiceRecord.builder()
                .shopId(shopId)
                .staffId(currentUser.getId())
                .customerId(entry.getCustomerId())
                .walkInQueueEntryId(entryId)
                .notes(request.notes())
                .productsUsed(request.productsUsed())
                .photoUrls(request.photoUrls())
                .build();

        return toResponse(serviceRecordRepository.save(record));
    }

    @Transactional(readOnly = true)
    public List<ServiceRecordResponse> getShopRecords(UUID shopId, User currentUser) {
        shopAccessService.verifyShopAccess(currentUser.getId(), shopId);
        return serviceRecordRepository.findByShopIdOrderByCreatedAtDesc(shopId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceRecordResponse> getMyStyleHistory(User currentUser) {
        return serviceRecordRepository.findByCustomerIdOrderByCreatedAtDesc(currentUser.getId())
                .stream().map(this::toResponse).toList();
    }

    private ServiceRecordResponse toResponse(ServiceRecord r) {
        return new ServiceRecordResponse(
                r.getId(),
                r.getShopId(),
                r.getStaffId(),
                r.getCustomerId(),
                r.getBookingId(),
                r.getWalkInQueueEntryId(),
                r.getNotes(),
                r.getProductsUsed(),
                r.getPhotoUrls(),
                r.getCreatedAt()
        );
    }
}
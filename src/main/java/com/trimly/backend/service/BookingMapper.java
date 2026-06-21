package com.trimly.backend.service;

import com.trimly.backend.dto.booking.BookedServiceResponse;
import com.trimly.backend.dto.booking.BookingResponse;
import com.trimly.backend.entity.Booking;
import com.trimly.backend.entity.BookingServiceItem;
import com.trimly.backend.entity.ServiceItem;
import com.trimly.backend.repository.BookingServiceItemRepository;
import com.trimly.backend.repository.ServiceItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class BookingMapper {

    private final BookingServiceItemRepository bookingServiceItemRepository;
    private final ServiceItemRepository serviceItemRepository;

    public BookingResponse toResponse(Booking booking) {
        List<BookingServiceItem> bookingServices = bookingServiceItemRepository.findByBookingId(booking.getId());
        List<ServiceItem> services = serviceItemRepository.findAllById(
                bookingServices.stream().map(BookingServiceItem::getServiceId).collect(Collectors.toList())
        );
        return toResponse(booking, bookingServices, services);
    }

    public BookingResponse toResponse(Booking booking, List<BookingServiceItem> bookingServices, List<ServiceItem> services) {
        Map<UUID, String> serviceNamesById = services.stream()
                .collect(Collectors.toMap(ServiceItem::getId, ServiceItem::getName));

        List<BookedServiceResponse> serviceResponses = bookingServices.stream()
                .map(bs -> new BookedServiceResponse(
                        bs.getServiceId(),
                        serviceNamesById.get(bs.getServiceId()),
                        bs.getPriceAtBooking()
                ))
                .collect(Collectors.toList());

        BigDecimal total = bookingServices.stream()
                .map(BookingServiceItem::getPriceAtBooking)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new BookingResponse(
                booking.getId(),
                booking.getShopId(),
                booking.getCustomerId(),
                booking.getStaffId(),
                booking.getGuestName(),
                booking.getGuestPhone(),
                booking.getBookingDate(),
                booking.getTimeSlot(),
                booking.getStatus(),
                serviceResponses,
                total,
                booking.getCreatedAt()
        );
    }

    public List<BookingResponse> toResponseList(List<Booking> bookings) {
        if (bookings.isEmpty()) {
            return List.of();
        }

        List<UUID> bookingIds = bookings.stream().map(Booking::getId).collect(Collectors.toList());
        List<BookingServiceItem> allItems = bookingServiceItemRepository.findByBookingIdIn(bookingIds);

        List<UUID> serviceIds = allItems.stream().map(BookingServiceItem::getServiceId).collect(Collectors.toList());
        List<ServiceItem> allServices = serviceItemRepository.findAllById(serviceIds);

        Map<UUID, List<BookingServiceItem>> itemsByBookingId = allItems.stream()
                .collect(Collectors.groupingBy(BookingServiceItem::getBookingId));
        Map<UUID, String> serviceNamesById = allServices.stream()
                .collect(Collectors.toMap(ServiceItem::getId, ServiceItem::getName));

        return bookings.stream()
                .map(booking -> {
                    List<BookingServiceItem> items = itemsByBookingId.getOrDefault(booking.getId(), List.of());

                    List<BookedServiceResponse> serviceResponses = items.stream()
                            .map(bs -> new BookedServiceResponse(
                                    bs.getServiceId(),
                                    serviceNamesById.get(bs.getServiceId()),
                                    bs.getPriceAtBooking()
                            ))
                            .collect(Collectors.toList());

                    BigDecimal total = items.stream()
                            .map(BookingServiceItem::getPriceAtBooking)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    return new BookingResponse(
                            booking.getId(),
                            booking.getShopId(),
                            booking.getCustomerId(),
                            booking.getStaffId(),
                            booking.getGuestName(),
                            booking.getGuestPhone(),
                            booking.getBookingDate(),
                            booking.getTimeSlot(),
                            booking.getStatus(),
                            serviceResponses,
                            total,
                            booking.getCreatedAt()
                    );
                })
                .collect(Collectors.toList());
    }


}

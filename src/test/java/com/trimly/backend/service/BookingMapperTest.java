package com.trimly.backend.service;

import com.trimly.backend.dto.booking.BookingResponse;
import com.trimly.backend.entity.Booking;
import com.trimly.backend.entity.BookingServiceItem;
import com.trimly.backend.entity.ServiceItem;
import com.trimly.backend.enums.BookingStatus;
import com.trimly.backend.enums.ServiceCategory;
import com.trimly.backend.repository.BookingServiceItemRepository;
import com.trimly.backend.repository.ServiceItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingMapperTest {

    @Mock private BookingServiceItemRepository bookingServiceItemRepository;
    @Mock private ServiceItemRepository serviceItemRepository;

    @InjectMocks
    private BookingMapper bookingMapper;

    private UUID bookingId;
    private UUID serviceId;
    private UUID shopId;
    private Booking booking;
    private ServiceItem serviceItem;
    private BookingServiceItem bookingServiceItem;

    @BeforeEach
    void setUp() {
        bookingId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
        shopId = UUID.randomUUID();

        booking = Booking.builder()
                .id(bookingId)
                .shopId(shopId)
                .staffId(UUID.randomUUID())
                .bookingDate(LocalDate.now())
                .timeSlot(LocalTime.of(10, 0))
                .status(BookingStatus.PENDING)
                .build();

        serviceItem = ServiceItem.builder()
                .id(serviceId)
                .shopId(shopId)
                .name("Haircut")
                .price(new BigDecimal("200.00"))
                .category(ServiceCategory.MALE)
                .build();

        bookingServiceItem = BookingServiceItem.builder()
                .id(UUID.randomUUID())
                .bookingId(bookingId)
                .serviceId(serviceId)
                .priceAtBooking(new BigDecimal("200.00"))
                .build();
    }

    @Test
    void toResponse_singleBooking_mapsCorrectly() {
        when(bookingServiceItemRepository.findByBookingId(bookingId)).thenReturn(List.of(bookingServiceItem));
        when(serviceItemRepository.findAllById(List.of(serviceId))).thenReturn(List.of(serviceItem));

        BookingResponse response = bookingMapper.toResponse(booking);

        assertThat(response.id()).isEqualTo(bookingId);
        assertThat(response.shopId()).isEqualTo(shopId);
        assertThat(response.status()).isEqualTo(BookingStatus.PENDING);
        assertThat(response.services()).hasSize(1);
        assertThat(response.services().get(0).serviceName()).isEqualTo("Haircut");
        assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
    }

    @Test
    void toResponse_withBookingServicesAndServicesList_calculatesTotalCorrectly() {
        UUID serviceId2 = UUID.randomUUID();

        ServiceItem serviceItem2 = ServiceItem.builder()
                .id(serviceId2)
                .shopId(shopId)
                .name("Beard Trim")
                .price(new BigDecimal("100.00"))
                .category(ServiceCategory.MALE)
                .build();

        BookingServiceItem bookingServiceItem2 = BookingServiceItem.builder()
                .id(UUID.randomUUID())
                .bookingId(bookingId)
                .serviceId(serviceId2)
                .priceAtBooking(new BigDecimal("100.00"))
                .build();

        BookingResponse response = bookingMapper.toResponse(
                booking,
                List.of(bookingServiceItem, bookingServiceItem2),
                List.of(serviceItem, serviceItem2)
        );

        assertThat(response.services()).hasSize(2);
        assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("300.00"));
    }

    @Test
    void toResponseList_emptyList_returnsEmpty() {
        List<BookingResponse> response = bookingMapper.toResponseList(List.of());

        assertThat(response).isEmpty();
    }

    @Test
    void toResponseList_multipleBookings_mapsAll() {
        UUID bookingId2 = UUID.randomUUID();

        Booking booking2 = Booking.builder()
                .id(bookingId2)
                .shopId(shopId)
                .staffId(UUID.randomUUID())
                .bookingDate(LocalDate.now())
                .timeSlot(LocalTime.of(11, 0))
                .status(BookingStatus.ACCEPTED)
                .build();

        BookingServiceItem item2 = BookingServiceItem.builder()
                .id(UUID.randomUUID())
                .bookingId(bookingId2)
                .serviceId(serviceId)
                .priceAtBooking(new BigDecimal("200.00"))
                .build();

        when(bookingServiceItemRepository.findByBookingIdIn(List.of(bookingId, bookingId2)))
                .thenReturn(List.of(bookingServiceItem, item2));
        when(serviceItemRepository.findAllById(List.of(serviceId, serviceId)))
                .thenReturn(List.of(serviceItem));

        List<BookingResponse> response = bookingMapper.toResponseList(List.of(booking, booking2));

        assertThat(response).hasSize(2);
        assertThat(response.get(0).id()).isEqualTo(bookingId);
        assertThat(response.get(1).id()).isEqualTo(bookingId2);
    }

    @Test
    void toResponseList_bookingWithNoServices_returnszeroTotal() {
        when(bookingServiceItemRepository.findByBookingIdIn(List.of(bookingId))).thenReturn(List.of());
        when(serviceItemRepository.findAllById(List.of())).thenReturn(List.of());

        List<BookingResponse> response = bookingMapper.toResponseList(List.of(booking));

        assertThat(response).hasSize(1);
        assertThat(response.get(0).totalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.get(0).services()).isEmpty();
    }
}
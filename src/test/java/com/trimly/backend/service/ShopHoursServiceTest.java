package com.trimly.backend.service;

import com.trimly.backend.dto.hours.ShopClosedDateRequest;
import com.trimly.backend.dto.hours.ShopClosedDateResponse;
import com.trimly.backend.dto.hours.ShopHoursRequest;
import com.trimly.backend.dto.hours.ShopHoursResponse;
import com.trimly.backend.entity.Shop;
import com.trimly.backend.entity.ShopClosedDate;
import com.trimly.backend.entity.ShopHours;
import com.trimly.backend.exception.ResourceNotFoundException;
import com.trimly.backend.repository.ShopClosedDateRepository;
import com.trimly.backend.repository.ShopHoursRepository;
import com.trimly.backend.repository.ShopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShopHoursServiceTest {

    @Mock private ShopHoursRepository shopHoursRepository;
    @Mock private ShopClosedDateRepository shopClosedDateRepository;
    @Mock private ShopRepository shopRepository;
    @Mock private ShopAccessService shopAccessService;

    @InjectMocks
    private ShopHoursService shopHoursService;

    private UUID shopId;
    private UUID ownerId;
    private Shop shop;

    // Monday = 1 in ISO
    private static final int MONDAY = 1;

    @BeforeEach
    void setUp() {
        shopId  = UUID.randomUUID();
        ownerId = UUID.randomUUID();

        shop = new Shop();
        shop.setId(shopId);
        shop.setName("Test Barbershop");
        shop.setOwnerId(ownerId);
        shop.setTimezone("Asia/Kolkata");
    }

    // ─── setHours ────────────────────────────────────────────────────────────────

    @Test
    void setHours_newDay_createsRecord() {
        ShopHoursRequest request = new ShopHoursRequest(MONDAY, false,
                LocalTime.of(9, 0), LocalTime.of(18, 0));

        ShopHours saved = ShopHours.builder()
                .id(UUID.randomUUID()).shopId(shopId).dayOfWeek(MONDAY)
                .openTime(LocalTime.of(9, 0)).closeTime(LocalTime.of(18, 0))
                .closed(false).build();

        doNothing().when(shopAccessService).verifyShopOwner(ownerId, shopId);
        when(shopHoursRepository.findByShopIdAndDayOfWeek(shopId, MONDAY)).thenReturn(Optional.empty());
        when(shopHoursRepository.save(any())).thenReturn(saved);

        ShopHoursResponse response = shopHoursService.setHours(shopId, request, ownerId);

        assertThat(response.dayOfWeek()).isEqualTo(MONDAY);
        assertThat(response.closed()).isFalse();
        assertThat(response.openTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(response.closeTime()).isEqualTo(LocalTime.of(18, 0));
        verify(shopHoursRepository).save(any());
    }

    @Test
    void setHours_existingDay_updatesRecord() {
        ShopHours existing = ShopHours.builder()
                .id(UUID.randomUUID()).shopId(shopId).dayOfWeek(MONDAY)
                .openTime(LocalTime.of(8, 0)).closeTime(LocalTime.of(17, 0))
                .closed(false).build();

        ShopHoursRequest request = new ShopHoursRequest(MONDAY, false,
                LocalTime.of(10, 0), LocalTime.of(20, 0));

        doNothing().when(shopAccessService).verifyShopOwner(ownerId, shopId);
        when(shopHoursRepository.findByShopIdAndDayOfWeek(shopId, MONDAY)).thenReturn(Optional.of(existing));
        when(shopHoursRepository.save(any())).thenReturn(existing);

        shopHoursService.setHours(shopId, request, ownerId);

        assertThat(existing.getOpenTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(existing.getCloseTime()).isEqualTo(LocalTime.of(20, 0));
        verify(shopHoursRepository).save(existing);
    }

    @Test
    void setHours_markDayClosed_clearsOpenAndCloseTimes() {
        ShopHoursRequest request = new ShopHoursRequest(MONDAY, true, null, null);

        ShopHours saved = ShopHours.builder()
                .id(UUID.randomUUID()).shopId(shopId).dayOfWeek(MONDAY)
                .openTime(null).closeTime(null).closed(true).build();

        doNothing().when(shopAccessService).verifyShopOwner(ownerId, shopId);
        when(shopHoursRepository.findByShopIdAndDayOfWeek(shopId, MONDAY)).thenReturn(Optional.empty());
        when(shopHoursRepository.save(any())).thenReturn(saved);

        ShopHoursResponse response = shopHoursService.setHours(shopId, request, ownerId);

        assertThat(response.closed()).isTrue();
        assertThat(response.openTime()).isNull();
        assertThat(response.closeTime()).isNull();

        ArgumentCaptor<ShopHours> captor = ArgumentCaptor.forClass(ShopHours.class);
        verify(shopHoursRepository).save(captor.capture());
        assertThat(captor.getValue().getOpenTime()).isNull();
        assertThat(captor.getValue().getCloseTime()).isNull();
    }

    @Test
    void setHours_openDayWithoutTimes_throws() {
        ShopHoursRequest request = new ShopHoursRequest(MONDAY, false, null, null);

        doNothing().when(shopAccessService).verifyShopOwner(ownerId, shopId);

        assertThatThrownBy(() -> shopHoursService.setHours(shopId, request, ownerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("openTime and closeTime are required");
    }

    @Test
    void setHours_openDayMissingCloseTime_throws() {
        ShopHoursRequest request = new ShopHoursRequest(MONDAY, false, LocalTime.of(9, 0), null);

        doNothing().when(shopAccessService).verifyShopOwner(ownerId, shopId);

        assertThatThrownBy(() -> shopHoursService.setHours(shopId, request, ownerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("openTime and closeTime are required");
    }

    // ─── getHours ────────────────────────────────────────────────────────────────

    @Test
    void getHours_returnsAllConfiguredDays() {
        ShopHours monday = ShopHours.builder()
                .id(UUID.randomUUID()).shopId(shopId).dayOfWeek(1)
                .openTime(LocalTime.of(9, 0)).closeTime(LocalTime.of(18, 0)).closed(false).build();
        ShopHours sunday = ShopHours.builder()
                .id(UUID.randomUUID()).shopId(shopId).dayOfWeek(7)
                .closed(true).build();

        when(shopHoursRepository.findByShopId(shopId)).thenReturn(List.of(monday, sunday));

        List<ShopHoursResponse> responses = shopHoursService.getHours(shopId);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).dayOfWeek()).isEqualTo(1);
        assertThat(responses.get(1).closed()).isTrue();
    }

    @Test
    void getHours_noConfiguredDays_returnsEmptyList() {
        when(shopHoursRepository.findByShopId(shopId)).thenReturn(List.of());

        assertThat(shopHoursService.getHours(shopId)).isEmpty();
    }

    // ─── addClosedDate ───────────────────────────────────────────────────────────

    @Test
    void addClosedDate_newDate_savesAndReturns() {
        LocalDate christmas = LocalDate.of(2025, 12, 25);
        ShopClosedDateRequest request = new ShopClosedDateRequest(christmas, "Christmas");

        ShopClosedDate saved = ShopClosedDate.builder()
                .id(UUID.randomUUID()).shopId(shopId)
                .closedDate(christmas).reason("Christmas").build();

        doNothing().when(shopAccessService).verifyShopOwner(ownerId, shopId);
        when(shopClosedDateRepository.existsByShopIdAndClosedDate(shopId, christmas)).thenReturn(false);
        when(shopClosedDateRepository.save(any())).thenReturn(saved);

        ShopClosedDateResponse response = shopHoursService.addClosedDate(shopId, request, ownerId);

        assertThat(response.closedDate()).isEqualTo(christmas);
        assertThat(response.reason()).isEqualTo("Christmas");
    }

    @Test
    void addClosedDate_duplicateDate_throws() {
        LocalDate date = LocalDate.of(2025, 12, 25);
        ShopClosedDateRequest request = new ShopClosedDateRequest(date, "Duplicate");

        doNothing().when(shopAccessService).verifyShopOwner(ownerId, shopId);
        when(shopClosedDateRepository.existsByShopIdAndClosedDate(shopId, date)).thenReturn(true);

        assertThatThrownBy(() -> shopHoursService.addClosedDate(shopId, request, ownerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already marked as closed");
    }

    // ─── removeClosedDate ────────────────────────────────────────────────────────

    @Test
    void removeClosedDate_existingDate_deletes() {
        UUID closedDateId = UUID.randomUUID();
        ShopClosedDate closedDate = ShopClosedDate.builder()
                .id(closedDateId).shopId(shopId)
                .closedDate(LocalDate.of(2025, 12, 25)).reason("Christmas").build();

        doNothing().when(shopAccessService).verifyShopOwner(ownerId, shopId);
        when(shopClosedDateRepository.findById(closedDateId)).thenReturn(Optional.of(closedDate));

        shopHoursService.removeClosedDate(shopId, closedDateId, ownerId);

        verify(shopClosedDateRepository).delete(closedDate);
    }

    @Test
    void removeClosedDate_notFound_throws() {
        UUID closedDateId = UUID.randomUUID();

        doNothing().when(shopAccessService).verifyShopOwner(ownerId, shopId);
        when(shopClosedDateRepository.findById(closedDateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shopHoursService.removeClosedDate(shopId, closedDateId, ownerId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Closed date not found");
    }

    @Test
    void removeClosedDate_belongsToDifferentShop_throws() {
        UUID closedDateId = UUID.randomUUID();
        ShopClosedDate foreignDate = ShopClosedDate.builder()
                .id(closedDateId).shopId(UUID.randomUUID()) // different shop
                .closedDate(LocalDate.of(2025, 12, 25)).build();

        doNothing().when(shopAccessService).verifyShopOwner(ownerId, shopId);
        when(shopClosedDateRepository.findById(closedDateId)).thenReturn(Optional.of(foreignDate));

        assertThatThrownBy(() -> shopHoursService.removeClosedDate(shopId, closedDateId, ownerId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── isOpenOn ────────────────────────────────────────────────────────────────

    @Test
    void isOpenOn_closedDateExists_returnsFalse() {
        LocalDate date = LocalDate.of(2025, 12, 25);
        when(shopClosedDateRepository.existsByShopIdAndClosedDate(shopId, date)).thenReturn(true);

        assertThat(shopHoursService.isOpenOn(shopId, date)).isFalse();
        verify(shopHoursRepository, never()).findByShopIdAndDayOfWeek(any(), anyInt());
    }

    @Test
    void isOpenOn_dayConfiguredAsOpen_returnsTrue() {
        LocalDate monday = LocalDate.of(2025, 6, 2); // a Monday
        ShopHours mondayHours = ShopHours.builder()
                .shopId(shopId).dayOfWeek(MONDAY)
                .openTime(LocalTime.of(9, 0)).closeTime(LocalTime.of(18, 0))
                .closed(false).build();

        when(shopClosedDateRepository.existsByShopIdAndClosedDate(shopId, monday)).thenReturn(false);
        when(shopHoursRepository.findByShopIdAndDayOfWeek(shopId, MONDAY)).thenReturn(Optional.of(mondayHours));

        assertThat(shopHoursService.isOpenOn(shopId, monday)).isTrue();
    }

    @Test
    void isOpenOn_dayConfiguredAsClosed_returnsFalse() {
        LocalDate sunday = LocalDate.of(2025, 6, 8); // a Sunday = dayOfWeek 7
        ShopHours sundayHours = ShopHours.builder()
                .shopId(shopId).dayOfWeek(7).closed(true).build();

        when(shopClosedDateRepository.existsByShopIdAndClosedDate(shopId, sunday)).thenReturn(false);
        when(shopHoursRepository.findByShopIdAndDayOfWeek(shopId, 7)).thenReturn(Optional.of(sundayHours));

        assertThat(shopHoursService.isOpenOn(shopId, sunday)).isFalse();
    }

    @Test
    void isOpenOn_noHoursConfigured_returnsFalse() {
        LocalDate date = LocalDate.of(2025, 6, 2);
        when(shopClosedDateRepository.existsByShopIdAndClosedDate(shopId, date)).thenReturn(false);
        when(shopHoursRepository.findByShopIdAndDayOfWeek(shopId, MONDAY)).thenReturn(Optional.empty());

        assertThat(shopHoursService.isOpenOn(shopId, date)).isFalse();
    }

    // ─── closingInstant ──────────────────────────────────────────────────────────

    @Test
    void closingInstant_openDay_returnsCloseTimeAsInstant() {
        LocalDate monday = LocalDate.of(2025, 6, 2);
        ShopHours mondayHours = ShopHours.builder()
                .shopId(shopId).dayOfWeek(MONDAY)
                .openTime(LocalTime.of(9, 0)).closeTime(LocalTime.of(18, 0))
                .closed(false).build();

        when(shopRepository.findById(shopId)).thenReturn(Optional.of(shop));
        when(shopClosedDateRepository.existsByShopIdAndClosedDate(shopId, monday)).thenReturn(false);
        when(shopHoursRepository.findByShopIdAndDayOfWeek(shopId, MONDAY)).thenReturn(Optional.of(mondayHours));

        Instant closingInstant = shopHoursService.closingInstant(shopId, monday);

        assertThat(closingInstant).isNotNull();
        // 18:00 IST = 12:30 UTC
        assertThat(closingInstant.toString()).contains("12:30:00Z");
    }

    @Test
    void closingInstant_closedDay_returnsNull() {
        LocalDate sunday = LocalDate.of(2025, 6, 8);

        when(shopRepository.findById(shopId)).thenReturn(Optional.of(shop));
        when(shopClosedDateRepository.existsByShopIdAndClosedDate(shopId, sunday)).thenReturn(true);

        assertThat(shopHoursService.closingInstant(shopId, sunday)).isNull();
    }

    @Test
    void closingInstant_shopNotFound_throws() {
        when(shopRepository.findById(shopId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shopHoursService.closingInstant(shopId, LocalDate.now()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Shop not found");
    }

    // ─── nextOpenInstant ─────────────────────────────────────────────────────────

    @Test
    void nextOpenInstant_duringOpenHours_returnsSameInstant() {
        // Monday 10:00 IST — shop is open 9–18
        LocalDate monday = LocalDate.of(2025, 6, 2);
        Instant tenAmIst = monday.atTime(LocalTime.of(4, 30)).atOffset(java.time.ZoneOffset.UTC).toInstant(); // 10:00 IST = 04:30 UTC

        ShopHours mondayHours = ShopHours.builder()
                .shopId(shopId).dayOfWeek(MONDAY)
                .openTime(LocalTime.of(9, 0)).closeTime(LocalTime.of(18, 0))
                .closed(false).build();

        when(shopRepository.findById(shopId)).thenReturn(Optional.of(shop));
        when(shopClosedDateRepository.existsByShopIdAndClosedDate(eq(shopId), any())).thenReturn(false);
        when(shopHoursRepository.findByShopIdAndDayOfWeek(shopId, MONDAY)).thenReturn(Optional.of(mondayHours));

        Instant result = shopHoursService.nextOpenInstant(shopId, tenAmIst);

        // Result should be the same instant (already within open hours)
        assertThat(result).isEqualTo(tenAmIst);
    }

    @Test
    void nextOpenInstant_beforeOpenTime_returnsOpenTime() {
        // Monday 7:00 IST — shop opens at 9:00
        LocalDate monday = LocalDate.of(2025, 6, 2);
        Instant sevenAmIst = monday.atTime(LocalTime.of(1, 30)).atOffset(java.time.ZoneOffset.UTC).toInstant(); // 07:00 IST = 01:30 UTC

        ShopHours mondayHours = ShopHours.builder()
                .shopId(shopId).dayOfWeek(MONDAY)
                .openTime(LocalTime.of(9, 0)).closeTime(LocalTime.of(18, 0))
                .closed(false).build();

        when(shopRepository.findById(shopId)).thenReturn(Optional.of(shop));
        when(shopClosedDateRepository.existsByShopIdAndClosedDate(eq(shopId), any())).thenReturn(false);
        when(shopHoursRepository.findByShopIdAndDayOfWeek(shopId, MONDAY)).thenReturn(Optional.of(mondayHours));

        Instant result = shopHoursService.nextOpenInstant(shopId, sevenAmIst);

        // Result should be 09:00 IST = 03:30 UTC
        assertThat(result.toString()).contains("03:30:00Z");
    }

    @Test
    void nextOpenInstant_afterClosingTime_skipsToNextOpenDay() {
        // Monday 20:00 IST (after close) — Tuesday is also open
        LocalDate monday = LocalDate.of(2025, 6, 2);
        LocalDate tuesday = LocalDate.of(2025, 6, 3); // dayOfWeek = 2
        Instant eightPmIst = monday.atTime(LocalTime.of(14, 30)).atOffset(java.time.ZoneOffset.UTC).toInstant(); // 20:00 IST = 14:30 UTC

        ShopHours mondayHours = ShopHours.builder()
                .shopId(shopId).dayOfWeek(MONDAY)
                .openTime(LocalTime.of(9, 0)).closeTime(LocalTime.of(18, 0))
                .closed(false).build();

        ShopHours tuesdayHours = ShopHours.builder()
                .shopId(shopId).dayOfWeek(2)
                .openTime(LocalTime.of(9, 0)).closeTime(LocalTime.of(18, 0))
                .closed(false).build();

        when(shopRepository.findById(shopId)).thenReturn(Optional.of(shop));
        when(shopClosedDateRepository.existsByShopIdAndClosedDate(eq(shopId), any())).thenReturn(false);
        when(shopHoursRepository.findByShopIdAndDayOfWeek(shopId, MONDAY)).thenReturn(Optional.of(mondayHours));
        when(shopHoursRepository.findByShopIdAndDayOfWeek(shopId, 2)).thenReturn(Optional.of(tuesdayHours));

        Instant result = shopHoursService.nextOpenInstant(shopId, eightPmIst);

        // Result should be Tuesday 09:00 IST = 03:30 UTC
        assertThat(result.toString()).contains("2025-06-03T03:30:00Z");
    }
}
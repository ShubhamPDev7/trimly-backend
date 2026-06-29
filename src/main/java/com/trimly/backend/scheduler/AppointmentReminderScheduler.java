package com.trimly.backend.scheduler;

import com.trimly.backend.entity.Booking;
import com.trimly.backend.entity.Shop;
import com.trimly.backend.entity.User;
import com.trimly.backend.repository.BookingRepository;
import com.trimly.backend.repository.ShopRepository;
import com.trimly.backend.repository.UserRepository;
import com.trimly.backend.service.FcmService;
import com.trimly.backend.service.OtpService;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentReminderScheduler {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final FcmService fcmService;

    @Value("${twilio.phone-number:}")
    private String twilioFromNumber;

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void sendReminders() {
        java.time.ZoneId ist = java.time.ZoneId.of("Asia/Kolkata");
        java.time.ZonedDateTime nowIst = java.time.ZonedDateTime.now(ist);
        LocalDate today = nowIst.toLocalDate();
        LocalTime oneHourFromNow = nowIst.toLocalTime().plusHours(1);
        LocalTime window = oneHourFromNow.plusMinutes(1);

        List<Booking> upcoming = bookingRepository.findUpcomingBookingsForReminder(
                today, oneHourFromNow, window);

        for (Booking booking : upcoming) {
            try {
                String shopName = shopRepository.findById(booking.getShopId())
                        .map(Shop::getName)
                        .orElse("your barbershop");

                String message = "Reminder: Your appointment at " + shopName +
                        " is in 1 hour at " + booking.getTimeSlot() + ". See you soon!";

                if (booking.getCustomerId() != null) {
                    userRepository.findById(booking.getCustomerId()).ifPresent(customer -> {
                        fcmService.sendToUser(customer.getId(), "Appointment Reminder", message);

                        if (customer.getPhone() != null && !twilioFromNumber.isBlank()) {
                            sendSms(customer.getPhone(), message);
                        }
                    });
                } else if (booking.getGuestPhone() != null && !twilioFromNumber.isBlank()) {
                    sendSms(booking.getGuestPhone(), message);
                }

                booking.setReminderSent(true);
                bookingRepository.save(booking);

            } catch (Exception e) {
                log.error("Failed to send reminder for booking {}: {}", booking.getId(), e.getMessage());
            }
        }

        if (!upcoming.isEmpty()) {
            log.info("Sent {} appointment reminders.", upcoming.size());
        }
    }

    private void sendSms(String phone, String message) {
        try {
            Message.creator(
                    new PhoneNumber(phone),
                    new PhoneNumber(twilioFromNumber),
                    message
            ).create();
        } catch (Exception e) {
            log.warn("SMS failed to {}: {}", phone, e.getMessage());
        }
    }
}
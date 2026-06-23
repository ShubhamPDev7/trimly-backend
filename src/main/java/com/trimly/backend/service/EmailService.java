package com.trimly.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

@Slf4j
@Service
public class EmailService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${resend.api.key}")
    private String resendApiKey;

    @Value("${app.from-email:Trimly <onboarding@resend.dev>}")
    private String fromEmail;



    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        send(toEmail,
                "Reset your Trimly password",
                "<p>Click the link below to reset your password. This link expires in 1 hour.</p>"
                        + "<p><a href=\"" + resetLink + "\">Reset Password</a></p>"
                        + "<p>If you didn't request this, you can safely ignore this email.</p>"
        );
    }



    public void sendBookingConfirmationToCustomer(
            String toEmail, String customerName, String shopName,
            LocalDate date, LocalTime timeSlot, String services) {
        send(toEmail,
                "Your booking at " + shopName + " is confirmed",
                "<p>Hi " + customerName + ",</p>"
                        + "<p>Your booking has been received and is pending confirmation from the shop.</p>"
                        + "<table style='border-collapse:collapse;margin-top:12px'>"
                        + row("Shop", shopName)
                        + row("Date", date.toString())
                        + row("Time", timeSlot.toString())
                        + row("Services", services)
                        + "</table>"
                        + "<p>We'll email you once the shop confirms your booking.</p>"
                        + "<p>— Trimly</p>"
        );
    }

    public void sendBookingAcceptedToCustomer(
            String toEmail, String customerName, String shopName,
            LocalDate date, LocalTime timeSlot) {
        send(toEmail,
                "Your booking at " + shopName + " is confirmed ✓",
                "<p>Hi " + customerName + ",</p>"
                        + "<p>Great news — the shop has confirmed your booking.</p>"
                        + "<table style='border-collapse:collapse;margin-top:12px'>"
                        + row("Shop", shopName)
                        + row("Date", date.toString())
                        + row("Time", timeSlot.toString())
                        + "</table>"
                        + "<p>See you there!</p>"
                        + "<p>— Trimly</p>"
        );
    }

    public void sendBookingRejectedToCustomer(
            String toEmail, String customerName, String shopName,
            LocalDate date, LocalTime timeSlot) {
        send(toEmail,
                "Update on your booking at " + shopName,
                "<p>Hi " + customerName + ",</p>"
                        + "<p>Unfortunately, the shop was unable to accept your booking for:</p>"
                        + "<table style='border-collapse:collapse;margin-top:12px'>"
                        + row("Shop", shopName)
                        + row("Date", date.toString())
                        + row("Time", timeSlot.toString())
                        + "</table>"
                        + "<p>You can try booking a different time slot on Trimly.</p>"
                        + "<p>— Trimly</p>"
        );
    }

    public void sendBookingCancelledToCustomer(
            String toEmail, String customerName, String shopName,
            LocalDate date, LocalTime timeSlot) {
        send(toEmail,
                "Your booking at " + shopName + " has been cancelled",
                "<p>Hi " + customerName + ",</p>"
                        + "<p>Your booking has been successfully cancelled.</p>"
                        + "<table style='border-collapse:collapse;margin-top:12px'>"
                        + row("Shop", shopName)
                        + row("Date", date.toString())
                        + row("Time", timeSlot.toString())
                        + "</table>"
                        + "<p>We hope to see you again soon.</p>"
                        + "<p>— Trimly</p>"
        );
    }



    public void sendNewBookingToOwner(
            String toEmail, String ownerName, String customerName,
            String shopName, LocalDate date, LocalTime timeSlot, String services) {
        send(toEmail,
                "New booking request at " + shopName,
                "<p>Hi " + ownerName + ",</p>"
                        + "<p>You have a new booking request.</p>"
                        + "<table style='border-collapse:collapse;margin-top:12px'>"
                        + row("Customer", customerName)
                        + row("Date", date.toString())
                        + row("Time", timeSlot.toString())
                        + row("Services", services)
                        + "</table>"
                        + "<p>Log in to Trimly to accept or reject this booking.</p>"
                        + "<p>— Trimly</p>"
        );
    }

    public void sendBookingCancelledToOwner(
            String toEmail, String ownerName, String customerName,
            String shopName, LocalDate date, LocalTime timeSlot) {
        send(toEmail,
                "Booking cancelled at " + shopName,
                "<p>Hi " + ownerName + ",</p>"
                        + "<p><strong>" + customerName + "</strong> has cancelled their booking.</p>"
                        + "<table style='border-collapse:collapse;margin-top:12px'>"
                        + row("Date", date.toString())
                        + row("Time", timeSlot.toString())
                        + "</table>"
                        + "<p>That slot is now free.</p>"
                        + "<p>— Trimly</p>"
        );
    }



    public void sendWalkInOwnerNotification(
            String toEmail, String ownerName, String shopName,
            String customerName, int position, int estimatedWaitMinutes, String services) {
        String waitText = estimatedWaitMinutes > 0
                ? estimatedWaitMinutes + " min estimated wait"
                : "Wait time calculating…";
        send(toEmail,
                "New walk-in at " + shopName,
                "<p>Hi " + ownerName + ",</p>"
                        + "<p><strong>" + customerName + "</strong> just joined your walk-in queue.</p>"
                        + "<table style='border-collapse:collapse;margin-top:12px'>"
                        + row("Queue position", "#" + position)
                        + row("Services", services)
                        + row("Estimated wait", waitText)
                        + "</table>"
                        + "<p>— Trimly</p>"
        );
    }

    public void sendQueueJoinConfirmation(
            String toEmail, String customerName, String shopName,
            int position, int estimatedWaitMinutes, String services) {
        String waitText = estimatedWaitMinutes > 0
                ? "Estimated wait: <strong>" + estimatedWaitMinutes + " minutes</strong>"
                : "Estimated wait: calculating…";
        send(toEmail,
                "You've joined the queue at " + shopName,
                "<p>Hi " + customerName + ",</p>"
                        + "<p>You're in the queue at <strong>" + shopName + "</strong>.</p>"
                        + "<table style='border-collapse:collapse;margin-top:12px'>"
                        + row("Queue position", "#" + position)
                        + row("Services", services)
                        + "</table>"
                        + "<p>" + waitText + "</p>"
                        + "<p>— Trimly</p>"
        );
    }



    public void sendNewReviewToOwner(
            String toEmail, String ownerName, String shopName,
            String reviewerName, int rating, String comment) {
        String stars = "★".repeat(rating) + "☆".repeat(5 - rating);
        String commentBlock = (comment != null && !comment.isBlank())
                ? "<p style='margin-top:8px;font-style:italic'>\"" + comment + "\"</p>"
                : "";
        send(toEmail,
                "New review for " + shopName,
                "<p>Hi " + ownerName + ",</p>"
                        + "<p><strong>" + reviewerName + "</strong> left a review for <strong>" + shopName + "</strong>.</p>"
                        + "<p style='font-size:20px'>" + stars + " (" + rating + "/5)</p>"
                        + commentBlock
                        + "<p>Log in to Trimly to reply to this review.</p>"
                        + "<p>— Trimly</p>"
        );
    }



    public void sendOwnerReplyToReviewer(
            String toEmail, String reviewerName, String shopName,
            String ownerReply, int rating) {
        String stars = "★".repeat(rating) + "☆".repeat(5 - rating);
        send(toEmail,
                shopName + " replied to your review",
                "<p>Hi " + reviewerName + ",</p>"
                        + "<p><strong>" + shopName + "</strong> has replied to your review.</p>"
                        + "<p style='font-size:18px'>" + stars + " (" + rating + "/5)</p>"
                        + "<p style='margin-top:12px;font-style:italic'>&ldquo;" + ownerReply + "&rdquo;</p>"
                        + "<p>— Trimly</p>"
        );
    }



    private void send(String toEmail, String subject, String html) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(resendApiKey);

            Map<String, Object> body = Map.of(
                    "from", fromEmail,
                    "to", new String[]{toEmail},
                    "subject", subject,
                    "html", html
            );

            restTemplate.postForEntity(
                    "https://api.resend.com/emails",
                    new HttpEntity<>(body, headers),
                    String.class
            );
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", toEmail, e.getMessage());
            // Fire-and-forget — never propagate email failures to the caller
        }
    }

    private String row(String label, String value) {
        return "<tr>"
                + "<td style='padding:4px 12px 4px 0;color:#666;font-size:14px'>" + label + "</td>"
                + "<td style='padding:4px 0;font-size:14px;font-weight:600'>" + value + "</td>"
                + "</tr>";
    }
}
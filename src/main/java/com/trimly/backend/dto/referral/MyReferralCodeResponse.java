package com.trimly.backend.dto.referral;

public record MyReferralCodeResponse(
        String referralCode,
        String shareMessage
) {}
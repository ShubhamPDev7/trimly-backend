package com.trimly.backend.service;

import com.trimly.backend.dto.barber.BarberProfileRequest;
import com.trimly.backend.dto.barber.BarberProfileResponse;
import com.trimly.backend.entity.BarberProfile;
import com.trimly.backend.entity.User;
import com.trimly.backend.exception.ResourceNotFoundException;
import com.trimly.backend.repository.BarberProfileRepository;
import com.trimly.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BarberProfileService {

    private final BarberProfileRepository barberProfileRepository;
    private final ShopAccessService shopAccessService;
    private final UserRepository userRepository;

    @Transactional
    public BarberProfileResponse upsertProfile(UUID shopId, UUID staffUserId,
                                               BarberProfileRequest request, UUID currentUserId) {
        shopAccessService.verifyShopAccess(currentUserId, shopId);

        if (!shopAccessService.hasShopAccess(staffUserId, shopId)) {
            throw new IllegalArgumentException("User does not belong to this shop.");
        }

        BarberProfile profile = barberProfileRepository
                .findByShopIdAndUserId(shopId, staffUserId)
                .orElse(BarberProfile.builder()
                        .shopId(shopId)
                        .userId(staffUserId)
                        .build());

        profile.setBio(request.bio());
        profile.setSpecialties(request.specialties());
        profile.setExperienceYears(request.experienceYears());
        profile.setInstagramHandle(request.instagramHandle());
        profile.setPhotoUrl(request.photoUrl());

        return toResponse(barberProfileRepository.save(profile));
    }

    @Transactional(readOnly = true)
    public List<BarberProfileResponse> getShopProfiles(UUID shopId) {
        return barberProfileRepository.findByShopId(shopId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public BarberProfileResponse getProfile(UUID shopId, UUID staffUserId) {
        BarberProfile profile = barberProfileRepository
                .findByShopIdAndUserId(shopId, staffUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Barber profile not found."));
        return toResponse(profile);
    }

    @Transactional
    public void deleteProfile(UUID shopId, UUID staffUserId, UUID currentUserId) {
        shopAccessService.verifyShopAccess(currentUserId, shopId);

        BarberProfile profile = barberProfileRepository
                .findByShopIdAndUserId(shopId, staffUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Barber profile not found."));

        barberProfileRepository.delete(profile);
    }

    private BarberProfileResponse toResponse(BarberProfile p) {
        String staffName = userRepository.findById(p.getUserId())
                .map(User::getName)
                .orElse("Unknown");

        return new BarberProfileResponse(
                p.getId(),
                p.getShopId(),
                p.getUserId(),
                staffName,
                p.getBio(),
                p.getSpecialties(),
                p.getExperienceYears(),
                p.getInstagramHandle(),
                p.getPhotoUrl(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
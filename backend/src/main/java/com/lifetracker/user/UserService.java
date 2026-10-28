package com.lifetracker.user;

import com.lifetracker.common.NotFoundException;
import com.lifetracker.user.dto.ProfileResponse;
import com.lifetracker.user.dto.UpdateProfileRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;

    public UserService(UserRepository userRepository, UserProfileRepository userProfileRepository) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
    }

    public ProfileResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        UserProfile profile = userProfileRepository.findById(userId)
                .orElseGet(() -> new UserProfile(userId));

        return new ProfileResponse(
                user.getEmail(),
                profile.getDisplayName(),
                profile.getDateOfBirth(),
                profile.getSexAtBirth(),
                profile.getHeightCm(),
                profile.getWeightKg(),
                profile.getTimezone()
        );
    }

    @Transactional
    public ProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        UserProfile profile = userProfileRepository.findById(userId)
                .orElseGet(() -> new UserProfile(userId));

        if (request.getTimezone() != null) {
            try {
                ZoneId.of(request.getTimezone());
                profile.setTimezone(request.getTimezone());
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid timezone: " + request.getTimezone());
            }
        }

        if (request.getDisplayName() != null) {
            profile.setDisplayName(request.getDisplayName());
        }
        if (request.getDateOfBirth() != null) {
            profile.setDateOfBirth(request.getDateOfBirth());
        }
        if (request.getSexAtBirth() != null) {
            profile.setSexAtBirth(request.getSexAtBirth());
        }
        if (request.getHeightCm() != null) {
            profile.setHeightCm(request.getHeightCm());
        }
        if (request.getWeightKg() != null) {
            profile.setWeightKg(request.getWeightKg());
        }

        profile.setUpdatedAt(Instant.now());
        profile = userProfileRepository.save(profile);

        return new ProfileResponse(
                user.getEmail(),
                profile.getDisplayName(),
                profile.getDateOfBirth(),
                profile.getSexAtBirth(),
                profile.getHeightCm(),
                profile.getWeightKg(),
                profile.getTimezone()
        );
    }

    @Transactional
    public void deleteAccount(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User not found");
        }
        userRepository.hardDeleteById(userId);
    }
}

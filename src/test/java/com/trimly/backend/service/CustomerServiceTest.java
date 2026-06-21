package com.trimly.backend.service;

import com.trimly.backend.dto.customer.CustomerProfileResponse;
import com.trimly.backend.dto.customer.UpdateProfileRequest;
import com.trimly.backend.entity.User;
import com.trimly.backend.repository.BookingRepository;
import com.trimly.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BookingMapper bookingMapper;

    @InjectMocks
    private CustomerService customerService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setName("Shubham");
        testUser.setEmail("shubham@test.com");
        testUser.setPhone("9999999999");
    }

    @Test
    void getMyProfile_returnsCorrectResponse() {
        CustomerProfileResponse response = customerService.getMyProfile(testUser);

        assertThat(response.name()).isEqualTo("Shubham");
        assertThat(response.email()).isEqualTo("shubham@test.com");
        assertThat(response.phone()).isEqualTo("9999999999");
    }

    @Test
    void updateMyProfile_updatesNameAndPhone() {
        UpdateProfileRequest request = new UpdateProfileRequest("New Name", "8888888888");
        when(userRepository.save(testUser)).thenReturn(testUser);

        CustomerProfileResponse response = customerService.updateMyProfile(request, testUser);

        assertThat(response.name()).isEqualTo("New Name");
        assertThat(response.phone()).isEqualTo("8888888888");
        verify(userRepository).save(testUser);
    }

    @Test
    void deleteMyAccount_setsDeletedTrueAndSaves() {
        when(userRepository.save(testUser)).thenReturn(testUser);

        customerService.deleteMyAccount(testUser);

        assertThat(testUser.isDeleted()).isTrue();
        assertThat(testUser.getDeletedAt()).isNotNull();
        verify(userRepository).save(testUser);
    }
}
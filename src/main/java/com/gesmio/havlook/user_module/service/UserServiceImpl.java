
package com.gesmio.havlook.user_module.service;

import com.gesmio.havlook.user_module.dto.ChangePasswordRequest;
import com.gesmio.havlook.user_module.dto.UserResponse;
import com.gesmio.havlook.user_module.dto.UserUpdateRequest;
import com.gesmio.havlook.user_module.entity.User;
import com.gesmio.havlook.user_module.exception.DuplicateResourceException;
import com.gesmio.havlook.user_module.exception.InvalidPasswordException;
import com.gesmio.havlook.user_module.exception.PasswordMismatchException;
import com.gesmio.havlook.user_module.exception.UserNotFoundException;
import com.gesmio.havlook.user_module.mapper.UserMapper;
import com.gesmio.havlook.user_module.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public UserResponse getMyProfile(String userId) {

        User user = getUserById(userId);

        return userMapper.toResponse(user);
    }

    @Override
    public UserResponse updateMyProfile(
            String userId,
            UserUpdateRequest request
    ) {

        User user = getUserById(userId);

        String newPhone = request.getPhone().trim();

        if (!newPhone.equals(user.getPhone())
                && userRepository.existsByPhone(newPhone)) {

            throw new DuplicateResourceException(
                    "Phone number is already registered"
            );
        }

        request.setPhone(newPhone);

        userMapper.updateEntity(user, request);

        User updatedUser = userRepository.save(user);

        return userMapper.toResponse(updatedUser);
    }

    @Override
    public void changePassword(
            String userId,
            ChangePasswordRequest request
    ) {

        User user = getUserById(userId);

        if (!passwordEncoder.matches(
                request.getCurrentPassword(),
                user.getPassword()
        )) {

            throw new InvalidPasswordException(
                    "Current password is incorrect"
            );
        }

        if (!request.getNewPassword()
                .equals(request.getConfirmPassword())) {

            throw new PasswordMismatchException(
                    "New password and confirm password do not match"
            );
        }

        if (passwordEncoder.matches(
                request.getNewPassword(),
                user.getPassword()
        )) {

            throw new InvalidPasswordException(
                    "New password must be different from current password"
            );
        }

        user.setPassword(
                passwordEncoder.encode(request.getNewPassword())
        );

        userRepository.save(user);
    }

    @Override
    public void deactivateMyAccount(String userId) {

        User user = getUserById(userId);

        user.setEnabled(false);

        userRepository.save(user);
    }

    private User getUserById(String userId) {

        if (userId == null || userId.isBlank()) {
            throw new UserNotFoundException(
                    "User ID is required"
            );
        }

        Long id;

        try {
            id = Long.valueOf(userId.trim());
        } catch (NumberFormatException ex) {
            throw new UserNotFoundException(
                    "Invalid user ID"
            );
        }

        return userRepository.findById(id)
                .orElseThrow(() ->
                        new UserNotFoundException(
                                "User not found"
                        )
                );
    }
}

package com.flux.user_management_service.user;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.flux.user_management_service.exception.UserAlreadyExistsException;
import com.flux.user_management_service.exception.UserNotFoundException;
import com.flux.user_management_service.user.dto.UserRequest;
import com.flux.user_management_service.user.dto.UserResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final com.flux.user_management_service.kafka.UserProducer userProducer;

    public String createUser(String keycloakId, UserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException("User with email " + request.email() + " already exists");
        }
        User user = User.builder()
                .keycloakId(keycloakId)
                .firstname(request.firstname())
                .lastname(request.lastname())
                .email(request.email())
                .birthday(request.birthday())
                .build();

        String userId = userRepository.save(user).getId();

        userProducer.sendUserEvent(com.flux.user_management_service.kafka.UserEvent.builder()
                .userId(userId)
                .email(user.getEmail())
                .firstname(user.getFirstname())
                .lastname(user.getLastname())
                .type("CREATED")
                .build());

        return userId;
    }

    @Cacheable(value = "users", key = "'id:' + #id")
    public UserResponse getUserById(String id) {
        return userRepository.findById(id)
                .map(userMapper::toUserResponse)
                .orElseThrow(() -> new UserNotFoundException("User not found!"));
    }

    @Cacheable(value = "users", key = "'keycloak:' + #keycloakId")
    public UserResponse getUserByKeycloakId(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId)
                .map(userMapper::toUserResponse)
                .orElseThrow(() -> new UserNotFoundException("User not found with Keycloak ID: " + keycloakId));
    }

    @CacheEvict(value = "users", allEntries = true)
    public void updateUser(String id, UserRequest request) {
        var user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found!"));

        mergeUser(user, request);

        userRepository.save(user);

        userProducer.sendUserEvent(com.flux.user_management_service.kafka.UserEvent.builder()
                .userId(id)
                .email(user.getEmail())
                .firstname(user.getFirstname())
                .lastname(user.getLastname())
                .type("UPDATED")
                .build());
    }

    private void mergeUser(User user, UserRequest request) {
        if (StringUtils.isNotBlank(request.firstname())) {
            user.setFirstname(request.firstname());
        }
        if (StringUtils.isNotBlank(request.lastname())) {
            user.setLastname(request.lastname());
        }
    }

    @CacheEvict(value = "users", allEntries = true)
    public void deleteUser(String id) {
        if (!userRepository.existsById(id)) {
            throw new UserNotFoundException("User not found!");
        }
        userRepository.deleteById(id);

        userProducer.sendUserEvent(com.flux.user_management_service.kafka.UserEvent.builder()
                .userId(id)
                .type("DELETED")
                .build());
    }

    public void syncAllUsers() {
        userRepository.findAll().forEach(user -> {
            userProducer.sendUserEvent(com.flux.user_management_service.kafka.UserEvent.builder()
                    .userId(user.getId())
                    .email(user.getEmail())
                    .firstname(user.getFirstname())
                    .lastname(user.getLastname())
                    .type("CREATED")
                    .build());
        });
    }
}

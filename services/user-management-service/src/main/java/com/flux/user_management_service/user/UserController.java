package com.flux.user_management_service.user;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.flux.user_management_service.user.dto.UserRequest;
import com.flux.user_management_service.user.dto.UserResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "User", description = "User management APIs")
public class UserController {

    private final UserService userService;

    @PostMapping("/add")
    @Operation(summary = "Create a new user", description = "Creates a new user with the provided details and Keycloak ID")
    public ResponseEntity<String> createUser(@RequestBody @Valid UserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID", description = "Retrieves a user by their unique ID")
    public ResponseEntity<UserResponse> getUserById(@PathVariable String id) {
        return ResponseEntity.status(HttpStatus.OK).body(userService.getUserById(id));
    }

    @GetMapping("/{id}/email")
    @Operation(summary = "Get user email by ID", description = "Internal helper for other services to resolve an email by user ID")
    public ResponseEntity<Map<String, String>> getUserEmailById(@PathVariable String id) {
        UserResponse user = userService.getUserById(id);
        return ResponseEntity.ok(Map.of("email", user.email()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update user", description = "Updates an existing user's information")
    public ResponseEntity<?> updateUser(@PathVariable String id, @RequestBody @Valid UserRequest request) {
        userService.updateUser(id, request);
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user", description = "Deletes a user by their ID")
    public ResponseEntity<?> deleteUser(@PathVariable String id) {
        userService.deleteUser(id);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @PostMapping("/sync")
    @Operation(summary = "Sync all users", description = "Triggers a Kafka event for every user in the database to sync with other services")
    public ResponseEntity<Void> syncAllUsers() {
        userService.syncAllUsers();
        return ResponseEntity.ok().build();
    }
}

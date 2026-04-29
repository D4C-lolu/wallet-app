package com.interswitch.walletapp.controllers.v1;

import com.interswitch.walletapp.annotation.ObserveParam;
import com.interswitch.walletapp.annotation.ValidSortField;
import com.interswitch.walletapp.constants.Permissions;
import com.interswitch.walletapp.models.enums.UserStatus;
import com.interswitch.walletapp.models.request.ChangePasswordRequest;
import com.interswitch.walletapp.models.request.CreateUserRequest;
import com.interswitch.walletapp.models.request.UpdateUserRequest;
import com.interswitch.walletapp.models.response.UserResponse;
import com.interswitch.walletapp.services.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "User Management", description = "Endpoints for managing system users, including role assignments, status updates, and password changes")
@RestController
@RequestMapping("users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(
            summary = "Create User (Admin)",
            description = "Register a new user account in the system. Requires USER_CREATE authority."
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    @PreAuthorize("hasAuthority('" + Permissions.USER_CREATE + "')")
    public UserResponse createUser(@RequestBody @Valid CreateUserRequest request) {
        return userService.createUser(request);
    }

    @Operation(
            summary = "List All Users",
            description = "Retrieve a paginated list of all system users with configurable sorting. Requires USER_READ authority."
    )
    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.USER_READ + "')")
    public Page<UserResponse> getAllUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "created_at") String sortField,
            @RequestParam(defaultValue = "DESC") Sort.Direction sortDirection
    ) {
        return userService.getAllUsers(page, size, sortField, sortDirection);
    }

    @Operation(summary = "Get User by ID", description = "Fetch detailed profile information for a specific user.")
    @GetMapping("{userId}")
    @PreAuthorize("hasAuthority('" + Permissions.USER_READ + "')")
    public UserResponse getUserById(@ObserveParam("userId") @PathVariable Long userId) {
        return userService.getUserById(userId);
    }

    @Operation(summary = "Update User Profile", description = "Modify user details like name or contact info. Requires USER_UPDATE authority.")
    @PutMapping("{userId}")
    @PreAuthorize("hasAuthority('" + Permissions.USER_UPDATE + "')")
    public UserResponse updateUser(
            @ObserveParam("userId") @PathVariable Long userId,
            @RequestBody @Valid UpdateUserRequest request
    ) {
        return userService.updateUser(userId, request);
    }

    @Operation(summary = "Change User Status", description = "Toggle user account status (e.g., ACTIVE, INACTIVE).")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PatchMapping("{userId}/status")
    @PreAuthorize("hasAuthority('" + Permissions.USER_UPDATE + "')")
    public UserResponse changeUserStatus(
            @ObserveParam("userId") @PathVariable Long userId,
            @RequestParam UserStatus status
    ) {
        return userService.changeUserStatus(userId, status);
    }

    @Operation(summary = "Assign New Role", description = "Change a user's primary role. Requires USER_UPDATE authority.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PatchMapping("{userId}/role")
    @PreAuthorize("hasAuthority('" + Permissions.USER_UPDATE + "')")
    public UserResponse changeUserRole(
            @ObserveParam("userId") @PathVariable Long userId,
            @RequestParam Long roleId
    ) {
        return userService.changeUserRole(userId, roleId);
    }

    @Operation(
            summary = "Change Password",
            description = "Update the user's login credentials. No specific authority required, but usually restricted to self or admin via service logic."
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PatchMapping("{userId}/password")
    public void changePassword(
            @ObserveParam("userId") @PathVariable Long userId,
            @RequestBody @Valid ChangePasswordRequest request
    ) {
        userService.changePassword(userId, request);
    }

    @Operation(summary = "Delete User", description = "Soft delete a user account from the system. Requires USER_DELETE authority.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("{userId}")
    @PreAuthorize("hasAuthority('" + Permissions.USER_DELETE + "')")
    public void deleteUser(@ObserveParam("userId") @PathVariable Long userId) {
        userService.deleteUser(userId);
    }
}
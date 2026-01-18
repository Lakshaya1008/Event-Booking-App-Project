package com.event.tickets.services.impl;

import com.event.tickets.domain.dtos.UserApprovalDto;
import com.event.tickets.domain.entities.ApprovalStatus;
import com.event.tickets.domain.entities.User;
import com.event.tickets.exceptions.InvalidApprovalStateException;
import com.event.tickets.exceptions.UserNotFoundException;
import com.event.tickets.repositories.UserRepository;
import com.event.tickets.services.ApprovalService;
import com.event.tickets.services.KeycloakAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Approval Service Implementation
 *
 * Manages user account approval workflow with proper state transitions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalServiceImpl implements ApprovalService {

  private final UserRepository userRepository;
  private final KeycloakAdminService keycloakAdminService;

  @Override
  public Page<UserApprovalDto> getPendingApprovals(Pageable pageable) {
    log.debug("Fetching pending approvals, page: {}", pageable.getPageNumber());

    Page<User> pendingUsers = userRepository.findByApprovalStatus(ApprovalStatus.PENDING, pageable);

    return pendingUsers.map(this::toUserApprovalDto);
  }

  @Override
  @Transactional
  public void approveUser(UUID userId, UUID adminId) {
    log.info("Approving user: userId={}, adminId={}", userId, adminId);

    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException(
            String.format("User with ID '%s' not found", userId)
        ));

    if (user.getApprovalStatus() != ApprovalStatus.PENDING) {
      throw new InvalidApprovalStateException(
          String.format("Cannot approve user '%s' with status '%s'. Only PENDING users can be approved.",
              userId, user.getApprovalStatus())
      );
    }

    User admin = userRepository.findById(adminId)
        .orElseThrow(() -> new UserNotFoundException(
            String.format("Admin user with ID '%s' not found", adminId)
        ));

    user.setApprovalStatus(ApprovalStatus.APPROVED);
    user.setApprovedAt(LocalDateTime.now());
    user.setApprovedBy(admin);

    userRepository.save(user);

    log.info("User approved successfully: userId={}, adminId={}", userId, adminId);
  }

  @Override
  @Transactional
  public void rejectUser(UUID userId, UUID adminId, String reason) {
    log.info("Rejecting user: userId={}, adminId={}, reason={}", userId, adminId, reason);

    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException(
            String.format("User with ID '%s' not found", userId)
        ));

    if (user.getApprovalStatus() != ApprovalStatus.PENDING) {
      throw new InvalidApprovalStateException(
          String.format("Cannot reject user '%s' with status '%s'. Only PENDING users can be rejected.",
              userId, user.getApprovalStatus())
      );
    }

    User admin = userRepository.findById(adminId)
        .orElseThrow(() -> new UserNotFoundException(
            String.format("Admin user with ID '%s' not found", adminId)
        ));

    user.setApprovalStatus(ApprovalStatus.REJECTED);
    user.setApprovedAt(LocalDateTime.now());
    user.setApprovedBy(admin);
    user.setRejectionReason(reason);

    userRepository.save(user);

    // Optionally disable user in Keycloak to prevent login attempts
    try {
      keycloakAdminService.setUserEnabled(userId, false);
      log.info("User disabled in Keycloak: userId={}", userId);
    } catch (Exception e) {
      log.warn("Failed to disable user in Keycloak (non-critical): userId={}", userId, e);
      // Don't fail the rejection if Keycloak update fails
    }

    log.info("User rejected successfully: userId={}, adminId={}", userId, adminId);
  }

  @Override
  public Page<UserApprovalDto> getAllUsersWithApprovalStatus(Pageable pageable) {
    log.debug("Fetching all users with approval status, page: {}", pageable.getPageNumber());

    Page<User> allUsers = userRepository.findAll(pageable);

    return allUsers.map(this::toUserApprovalDto);
  }

  /**
   * Converts User entity to UserApprovalDto.
   * Fetches user roles from Keycloak for complete information.
   */
  private UserApprovalDto toUserApprovalDto(User user) {
    UserApprovalDto dto = new UserApprovalDto();
    dto.setUserId(user.getId().toString());
    dto.setName(user.getName());
    dto.setEmail(user.getEmail());
    dto.setApprovalStatus(user.getApprovalStatus().name());
    dto.setCreatedAt(user.getCreatedAt());
    dto.setRejectionReason(user.getRejectionReason());
    dto.setApprovedAt(user.getApprovedAt());

    if (user.getApprovedBy() != null) {
      dto.setApprovedByName(user.getApprovedBy().getName());
    }

    // Fetch roles from Keycloak
    try {
      dto.setRoles(keycloakAdminService.getUserRoles(user.getId()));
    } catch (Exception e) {
      log.warn("Failed to fetch roles for user: userId={}", user.getId(), e);
      dto.setRoles(java.util.Collections.emptyList());
    }

    return dto;
  }
}

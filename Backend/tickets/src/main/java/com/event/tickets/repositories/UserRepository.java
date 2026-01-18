package com.event.tickets.repositories;

import com.event.tickets.domain.entities.ApprovalStatus;
import com.event.tickets.domain.entities.User;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

  /**
   * Finds all users with a specific approval status.
   *
   * @param approvalStatus The approval status to filter by
   * @param pageable Pagination parameters
   * @return Page of users with the specified status
   */
  Page<User> findByApprovalStatus(ApprovalStatus approvalStatus, Pageable pageable);

  /**
   * Checks if a user with the given email already exists.
   *
   * @param email The email to check
   * @return true if user exists, false otherwise
   */
  boolean existsByEmail(String email);
}

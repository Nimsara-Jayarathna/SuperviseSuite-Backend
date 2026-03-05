package com.supervisesuite.backend.users.repository;

import com.supervisesuite.backend.users.entity.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link User} entities.
 *
 * <p>All query methods are derived automatically by Spring Data from
 * the method name convention — no JPQL or native SQL is required.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Looks up a user by their email address.
     *
     * @param email the email to search for
     * @return an {@link Optional} containing the user if found, or empty
     */
    Optional<User> findByEmail(String email);

    /**
     * Checks whether a user with the given email already exists.
     * Used for duplicate-email validation during registration.
     *
     * @param email the email to check
     * @return {@code true} if the email is already taken
     */
    boolean existsByEmail(String email);

    /**
     * Checks whether a user with the given registration number already exists.
     * Used for duplicate-registration-number validation during student registration.
     *
     * @param registrationNumber the registration number to check
     * @return {@code true} if the registration number is already taken
     */
    boolean existsByRegistrationNumber(String registrationNumber);

    List<User> findTop10ByRoleAndEmailContainingIgnoreCaseOrderByEmailAsc(String role, String email);
}

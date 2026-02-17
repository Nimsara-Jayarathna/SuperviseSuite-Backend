package com.supervisesuite.backend.users.repository;

import com.supervisesuite.backend.users.entity.User;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
}

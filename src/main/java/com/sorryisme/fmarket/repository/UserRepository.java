package com.sorryisme.fmarket.repository;

import com.sorryisme.fmarket.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

  boolean existsByNameAndPhoneNumber(String name, String phoneNumber);

  Optional<User> findByLoginId(String loginId);
}

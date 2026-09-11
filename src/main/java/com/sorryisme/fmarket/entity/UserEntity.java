package com.sorryisme.fmarket.entity;

import com.sorryisme.fmarket.enums.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** user 테이블. MySQL 예약어라 테이블명을 인용한다. */
@Entity
@Table(name = "\"user\"")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEntity extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "login_id", nullable = false, length = 50, unique = true)
  private String loginId;

  @Column(name = "password", nullable = false, length = 255)
  private String password;

  @Column(name = "salt", nullable = false, length = 255)
  private String salt;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "email", nullable = false, length = 100, unique = true)
  private String email;

  @Enumerated(EnumType.STRING)
  @Column(name = "role")
  private UserRole role;

  @Column(name = "phone_number", length = 20)
  private String phoneNumber;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  @Builder
  private UserEntity(
      Long id,
      String loginId,
      String password,
      String salt,
      String name,
      String email,
      UserRole role,
      String phoneNumber) {
    this.id = id;
    this.loginId = loginId;
    this.password = password;
    this.salt = salt;
    this.name = name;
    this.email = email;
    this.role = role;
    this.phoneNumber = phoneNumber;
  }

  /** 회원 정보 수정. 로그인 ID·비밀번호는 별도 흐름에서 다루므로 여기서 바꾸지 않는다. */
  public void updateProfile(String name, String email, String phoneNumber) {
    this.name = name;
    this.email = email;
    this.phoneNumber = phoneNumber;
  }

  public void changePassword(String password, String salt) {
    this.password = password;
    this.salt = salt;
  }

  public void softDelete(LocalDateTime deletedAt) {
    this.deletedAt = deletedAt;
  }
}

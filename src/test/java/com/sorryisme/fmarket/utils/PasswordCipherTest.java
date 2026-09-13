package com.sorryisme.fmarket.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PasswordCipherTest {

  private static final String RAW_PASSWORD = "password1234!";

  @Test
  @DisplayName("같은 비밀번호와 같은 salt 는 항상 같은 해시를 만든다")
  void encryptIsDeterministicForSameSalt() {
    String salt = PasswordCipher.getSalt();

    String first = PasswordCipher.encrypt(RAW_PASSWORD, salt);
    String second = PasswordCipher.encrypt(RAW_PASSWORD, salt);

    // UserService.login 은 저장된 salt 로 다시 해싱한 값이 저장된 해시와 같은지로 로그인을
    // 판정한다. 이 성질이 깨지면 모든 로그인이 실패한다.
    assertThat(first).isEqualTo(second);
  }

  @Test
  @DisplayName("salt 가 다르면 같은 비밀번호라도 다른 해시가 된다")
  void encryptDiffersBySalt() {
    String hash = PasswordCipher.encrypt(RAW_PASSWORD, PasswordCipher.getSalt());
    String otherHash = PasswordCipher.encrypt(RAW_PASSWORD, PasswordCipher.getSalt());

    assertThat(hash).isNotEqualTo(otherHash);
  }

  @Test
  @DisplayName("getSalt 는 호출마다 다른 BCrypt salt 를 만든다")
  void getSaltReturnsNewValueEachTime() {
    String salt = PasswordCipher.getSalt();
    String otherSalt = PasswordCipher.getSalt();

    assertThat(salt).isNotEqualTo(otherSalt);
    assertThat(salt).startsWith("$2a$");
  }

  @Test
  @DisplayName("checkPassword 는 해시를 만든 원래 비밀번호에만 true 를 준다")
  void checkPasswordMatchesOnlyOriginal() {
    String hash = PasswordCipher.encrypt(RAW_PASSWORD, PasswordCipher.getSalt());

    assertThat(PasswordCipher.checkPassword(RAW_PASSWORD, hash)).isTrue();
    assertThat(PasswordCipher.checkPassword("wrongPassword1234!", hash)).isFalse();
  }
}

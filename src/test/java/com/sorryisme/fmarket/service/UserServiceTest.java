package com.sorryisme.fmarket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sorryisme.fmarket.common.ErrorCode;
import com.sorryisme.fmarket.dto.request.SellerRequestDto;
import com.sorryisme.fmarket.dto.request.UserRequestDto;
import com.sorryisme.fmarket.dto.request.UserUpdateRequestDto;
import com.sorryisme.fmarket.dto.response.SellerResponseDto;
import com.sorryisme.fmarket.dto.response.UserResponseDto;
import com.sorryisme.fmarket.entity.Store;
import com.sorryisme.fmarket.entity.User;
import com.sorryisme.fmarket.exception.BusinessException;
import com.sorryisme.fmarket.repository.StoreRepository;
import com.sorryisme.fmarket.repository.UserRepository;
import com.sorryisme.fmarket.testUtils.DomainFixture;
import com.sorryisme.fmarket.utils.PasswordCipher;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserServiceTest {

  private final UserRepository userRepository = mock(UserRepository.class);
  private final StoreRepository storeRepository = mock(StoreRepository.class);
  private final UserService userService = new UserService(userRepository, storeRepository);

  @Test
  @DisplayName("유저 생성 시 중복된 유저를 생성하고자 하면 에러를 발생시킨다")
  void rejectsDuplicateUser() {
    when(userRepository.existsByNameAndPhoneNumber(anyString(), anyString())).thenReturn(true);

    assertThatThrownBy(() -> userService.createUser(createUserRequestDto()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.DUPLICATE_USER);
    verify(userRepository, never()).save(any(User.class));
  }

  @Test
  @DisplayName("유저 생성 시 비밀번호는 해시되어 저장되고 저장된 정보가 리턴된다")
  void hashesPasswordOnCreate() {
    when(userRepository.existsByNameAndPhoneNumber(anyString(), anyString())).thenReturn(false);
    AtomicReference<User> saved = new AtomicReference<>();
    when(userRepository.save(any(User.class)))
        .thenAnswer(
            inv -> {
              User user = inv.getArgument(0);
              saved.set(user);
              return user;
            });

    UserRequestDto requestDto = createUserRequestDto();
    UserResponseDto result = userService.createUser(requestDto);

    assertThat(result.getLoginId()).isEqualTo(requestDto.getLoginId());
    assertThat(result.getEmail()).isEqualTo(requestDto.getEmail());
    assertThat(result.getName()).isEqualTo(requestDto.getName());
    assertThat(saved.get().getPassword()).isNotEqualTo(requestDto.getPassword());
    assertThat(saved.get().getPassword())
        .isEqualTo(PasswordCipher.encrypt(requestDto.getPassword(), saved.get().getSalt()));
  }

  @Test
  @DisplayName("판매자 생성 시 유저와 상점이 함께 저장되고 저장된 정보가 리턴된다")
  void savesUserAndStoreForSeller() {
    when(userRepository.existsByNameAndPhoneNumber(anyString(), anyString())).thenReturn(false);
    when(userRepository.save(any(User.class))).thenReturn(DomainFixture.createUser(10L));
    when(storeRepository.save(any(Store.class))).thenAnswer(inv -> inv.getArgument(0));

    SellerRequestDto requestDto = createSellerRequestDto();
    SellerResponseDto result = userService.createSeller(requestDto);

    verify(storeRepository).save(argThat(store -> store.getUserId() == 10L));
    assertThat(result.getLoginId()).isEqualTo("testUser");
    assertThat(result.getEmail()).isEqualTo("test@naver.com");
    assertThat(result.getName()).isEqualTo("테스트유저");
    assertThat(result.getPhoneNumber()).isEqualTo("01012345678");
    assertThat(result.getStoreName()).isEqualTo(requestDto.getStoreName());
    assertThat(result.getBusinessNumber()).isEqualTo(requestDto.getBusinessNumber());
  }

  @Test
  @DisplayName("유저 업데이트 시 존재하지 않는 유저라면 예외를 발생시킨다")
  void throwsWhenUpdatingMissingUser() {
    when(userRepository.findById(anyLong())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.updateUser(createUpdateRequestDto(), 1L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.USER_NOT_FOUND);
  }

  @Test
  @DisplayName("유저 업데이트 시 엔티티가 수정되고 수정된 유저 ID를 반환한다")
  void updatesUserByDirtyChecking() {
    User user = DomainFixture.createUser(1L);
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));

    Long result = userService.updateUser(createUpdateRequestDto(), 1L);

    assertThat(result).isEqualTo(1L);
    assertThat(user.getName()).isEqualTo("변경된 이름");
    assertThat(user.getEmail()).isEqualTo("updated_email@naver.com");
    assertThat(user.getPhoneNumber()).isEqualTo("01098765432");
  }

  @Test
  @DisplayName("로그인 시 유저가 없는 경우 에러를 발생시킨다")
  void throwsWhenLoginUserMissing() {
    when(userRepository.findByLoginId(anyString())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.login("null", "1234"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.LOGIN_FAILED);
  }

  @Test
  @DisplayName("로그인 시 비밀번호 틀린 경우 에러를 발생시킨다")
  void throwsWhenPasswordMismatch() {
    when(userRepository.findByLoginId(anyString())).thenReturn(Optional.of(createUser()));

    assertThatThrownBy(() -> userService.login("testUser", "1234567"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.LOGIN_FAILED);
  }

  @Test
  @DisplayName("로그인 성공 시 id를 반환한다")
  void returnsIdOnLogin() {
    when(userRepository.findByLoginId(anyString())).thenReturn(Optional.of(createUser()));

    assertThat(userService.login("testUser", "xptmxm")).isEqualTo(1L);
  }

  private static UserRequestDto createUserRequestDto() {
    return UserRequestDto.builder()
        .loginId("testUser")
        .password("xptmxmqlalfqjsgh!")
        .name("테스트유저")
        .email("test@naver.com")
        .phoneNumber("01012345678")
        .build();
  }

  private static SellerRequestDto createSellerRequestDto() {
    return SellerRequestDto.builder()
        .loginId("testUser")
        .password("xptmxmqlalfqjsgh!")
        .name("테스트유저")
        .email("test@naver.com")
        .phoneNumber("01012345678")
        .storeName("테스트 상점")
        .businessNumber("012-12-34567")
        .logoUrl("https://naver.com/test.jpg")
        .description("설명테스트")
        .build();
  }

  private static UserUpdateRequestDto createUpdateRequestDto() {
    return UserUpdateRequestDto.builder()
        .name("변경된 이름")
        .email("updated_email@naver.com")
        .phoneNumber("01098765432")
        .build();
  }

  private static User createUser() {
    String salt = PasswordCipher.getSalt();
    return User.builder()
        .id(1L)
        .loginId("testUser")
        .password(PasswordCipher.encrypt("xptmxm", salt))
        .salt(salt)
        .name("테스트유저")
        .email("test@naver.com")
        .phoneNumber("01012345678")
        .build();
  }
}

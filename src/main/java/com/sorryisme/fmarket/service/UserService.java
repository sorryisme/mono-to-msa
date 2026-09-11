package com.sorryisme.fmarket.service;

import com.sorryisme.fmarket.dto.request.SellerRequestDto;
import com.sorryisme.fmarket.dto.request.UserRequestDto;
import com.sorryisme.fmarket.dto.request.UserUpdateRequestDto;
import com.sorryisme.fmarket.dto.response.SellerResponseDto;
import com.sorryisme.fmarket.dto.response.UserResponseDto;
import com.sorryisme.fmarket.entity.Store;
import com.sorryisme.fmarket.entity.User;
import com.sorryisme.fmarket.exception.DuplicateDataException;
import com.sorryisme.fmarket.exception.NotFoundDataException;
import com.sorryisme.fmarket.repository.StoreRepository;
import com.sorryisme.fmarket.repository.UserRepository;
import com.sorryisme.fmarket.utils.PasswordCipher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class UserService {

  private final UserRepository userRepository;
  private final StoreRepository storeRepository;

  @Transactional
  public UserResponseDto createUser(UserRequestDto userRequestDto) {

    validateExistUser(userRequestDto.getName(), userRequestDto.getPhoneNumber());
    User saveUser = saveUserByDto(userRequestDto);

    return UserResponseDto.from(saveUser);
  }

  @Transactional
  public long updateUser(UserUpdateRequestDto userUpdateRequestDto, long userId) {

    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new NotFoundDataException("찾을 수 없는 유저입니다"));

    // 변경 감지로 UPDATE 가 나간다.
    user.updateProfile(
        userUpdateRequestDto.getName(),
        userUpdateRequestDto.getEmail(),
        userUpdateRequestDto.getPhoneNumber());

    return userId;
  }

  @Transactional
  public SellerResponseDto createSeller(SellerRequestDto sellerRequestDto) {

    validateExistUser(sellerRequestDto.getName(), sellerRequestDto.getPhoneNumber());
    User saveUser = saveUserByDto(sellerRequestDto);

    Store saveStore =
        storeRepository.save(
            Store.builder()
                .storeName(sellerRequestDto.getStoreName())
                .logoUrl(sellerRequestDto.getLogoUrl())
                .description(sellerRequestDto.getDescription())
                .businessNumber(sellerRequestDto.getBusinessNumber())
                .userId(saveUser.getId())
                .build());

    return SellerResponseDto.from(saveUser, saveStore);
  }

  @Transactional(readOnly = true)
  public Long login(String loginId, String password) throws IllegalArgumentException {
    User user =
        userRepository
            .findByLoginId(loginId)
            .orElseThrow(() -> new IllegalArgumentException("찾을 수 없는 유저입니다."));

    String hashedPassword = PasswordCipher.encrypt(password, user.getSalt());

    if (hashedPassword.equals(user.getPassword())) {
      return user.getId();
    }

    throw new IllegalArgumentException("로그인정보가 일치하지 않습니다.");
  }

  private void validateExistUser(String username, String phoneNumber)
      throws DuplicateDataException {
    if (userRepository.existsByNameAndPhoneNumber(username, phoneNumber))
      throw new DuplicateDataException("이미 등록된 유저입니다.");
  }

  private User saveUserByDto(UserRequestDto userRequestDto) {
    String salt = PasswordCipher.getSalt();
    String hashPassword = PasswordCipher.encrypt(userRequestDto.getPassword(), salt);

    return userRepository.save(
        User.builder()
            .loginId(userRequestDto.getLoginId())
            .name(userRequestDto.getName())
            .email(userRequestDto.getEmail())
            .phoneNumber(userRequestDto.getPhoneNumber())
            .salt(salt)
            .password(hashPassword)
            .build());
  }
}

package com.sorryisme.fmarket.service;

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
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

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
  public Long login(String loginId, String password) {
    User user =
        userRepository
            .findByLoginId(loginId)
            .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));

    String hashedPassword = PasswordCipher.encrypt(password, user.getSalt());

    if (hashedPassword.equals(user.getPassword())) {
      return user.getId();
    }

    throw new BusinessException(ErrorCode.LOGIN_FAILED);
  }

  private void validateExistUser(String username, String phoneNumber) {
    if (userRepository.existsByNameAndPhoneNumber(username, phoneNumber))
      throw new BusinessException(ErrorCode.DUPLICATE_USER);
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

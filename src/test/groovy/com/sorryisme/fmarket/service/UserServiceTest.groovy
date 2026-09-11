package com.sorryisme.fmarket.service

import com.sorryisme.fmarket.dto.request.SellerRequestDto
import com.sorryisme.fmarket.dto.request.UserRequestDto
import com.sorryisme.fmarket.dto.request.UserUpdateRequestDto
import com.sorryisme.fmarket.entity.Store
import com.sorryisme.fmarket.entity.User
import com.sorryisme.fmarket.exception.DuplicateDataException
import com.sorryisme.fmarket.exception.NotFoundDataException
import com.sorryisme.fmarket.repository.StoreRepository
import com.sorryisme.fmarket.repository.UserRepository
import com.sorryisme.fmarket.testUtils.DomainFixture
import com.sorryisme.fmarket.utils.PasswordCipher
import spock.lang.Specification

class UserServiceTest extends Specification {

    UserRepository userRepository = Mock()
    StoreRepository storeRepository = Mock()
    UserService userService = new UserService(userRepository, storeRepository)

    def "유저 생성 시 중복된 유저를 생성하고자 하면 에러를 발생시킨다"() {
        given:
        userRepository.existsByNameAndPhoneNumber(_ as String, _ as String) >> true

        when:
        userService.createUser(createUserRequestDto())

        then:
        def e = thrown(DuplicateDataException.class)
        e.getMessage() == "이미 등록된 유저입니다."
        0 * userRepository.save(_)
    }

    def "유저 생성 시 비밀번호는 해시되어 저장되고 저장된 정보가 리턴된다"() {
        given:
        userRepository.existsByNameAndPhoneNumber(_ as String, _ as String) >> false
        User saved = null
        userRepository.save(_ as User) >> { User u -> saved = u; u }

        when:
        def requestDto = createUserRequestDto()
        def result = userService.createUser(requestDto)

        then:
        result.getLoginId() == requestDto.getLoginId()
        result.getEmail() == requestDto.getEmail()
        result.getName() == requestDto.getName()
        saved.getPassword() != requestDto.getPassword()
        saved.getPassword() == PasswordCipher.encrypt(requestDto.getPassword(), saved.getSalt())
    }

    def "판매자 생성 시 유저와 상점이 함께 저장되고 저장된 정보가 리턴된다"() {
        given:
        userRepository.existsByNameAndPhoneNumber(_ as String, _ as String) >> false
        userRepository.save(_ as User) >> { User u -> DomainFixture.createUser(10L) }
        storeRepository.save(_ as Store) >> { Store s -> s }

        when:
        def requestDto = createSellerRequestDto()
        def result = userService.createSeller(requestDto)

        then:
        1 * storeRepository.save({ Store s -> s.getUserId() == 10L }) >> { Store s -> s }
        result.getLoginId() == "testUser"
        result.getEmail() == "test@naver.com"
        result.getName() == "테스트유저"
        result.getPhoneNumber() == "01012345678"
        result.getStoreName() == requestDto.getStoreName()
        result.getBusinessNumber() == requestDto.getBusinessNumber()
    }

    def "유저 업데이트 시 존재하지 않는 유저라면 예외를 발생시킨다"() {
        given:
        userRepository.findById(_ as Long) >> Optional.empty()

        when:
        userService.updateUser(createUpdateRequestDto(), 1L)

        then:
        def e = thrown(NotFoundDataException)
        e.getMessage() == "찾을 수 없는 유저입니다"
    }

    def "유저 업데이트 시 엔티티가 수정되고 수정된 유저 ID를 반환한다"() {
        given:
        User user = DomainFixture.createUser(1L)
        userRepository.findById(1L) >> Optional.of(user)

        when:
        def result = userService.updateUser(createUpdateRequestDto(), 1L)

        then:
        result == 1L
        user.getName() == "변경된 이름"
        user.getEmail() == "updated_email@naver.com"
        user.getPhoneNumber() == "01098765432"
    }

    def "로그인 시 유저가 없는 경우 에러를 발생시킨다"() {
        given:
        userRepository.findByLoginId(_ as String) >> Optional.empty()

        when:
        userService.login("null", "1234")

        then:
        def e = thrown(IllegalArgumentException)
        e.getMessage() == "찾을 수 없는 유저입니다."
    }

    def "로그인 시 비밀번호 틀린 경우 에러를 발생시킨다"() {
        given:
        userRepository.findByLoginId(_ as String) >> Optional.of(createUser())

        when:
        userService.login("testUser", "1234567")

        then:
        def e = thrown(IllegalArgumentException)
        e.getMessage() == "로그인정보가 일치하지 않습니다."
    }

    def "로그인 성공 시 id를 반환한다"() {
        given:
        userRepository.findByLoginId(_ as String) >> Optional.of(createUser())

        when:
        Long id = userService.login("testUser", "xptmxm")

        then:
        id == 1L
    }

    private static UserRequestDto createUserRequestDto() {
        return UserRequestDto.builder()
                .loginId("testUser")
                .password("xptmxmqlalfqjsgh!")
                .name("테스트유저")
                .email("test@naver.com")
                .phoneNumber("01012345678")
                .build()
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
                .build()
    }

    private static UserUpdateRequestDto createUpdateRequestDto() {
        return UserUpdateRequestDto.builder()
                .name("변경된 이름")
                .email("updated_email@naver.com")
                .phoneNumber("01098765432")
                .build()
    }

    private static User createUser() {
        def salt = PasswordCipher.getSalt()
        return User.builder()
                .id(1L)
                .loginId("testUser")
                .password(PasswordCipher.encrypt("xptmxm", salt))
                .salt(salt)
                .name("테스트유저")
                .email("test@naver.com")
                .phoneNumber("01012345678")
                .build()
    }
}

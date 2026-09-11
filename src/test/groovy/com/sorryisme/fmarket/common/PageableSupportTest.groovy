package com.sorryisme.fmarket.common

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import spock.lang.Specification

class PageableSupportTest extends Specification {

    def "정렬이 없으면 id 정렬을 붙인다"() {
        when:
        Pageable result = PageableSupport.withStableSort(PageRequest.of(1, 20))

        then:
        result.getPageNumber() == 1
        result.getPageSize() == 20
        result.getSort() == Sort.by("id")
    }

    def "정렬이 있어도 id 가 없으면 맨 뒤에 id 보조 정렬을 붙인다"() {
        when:
        Pageable result = PageableSupport.withStableSort(
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "orderDate")))

        then:
        result.getSort().toList()*.getProperty() == ["orderDate", "id"]
        result.getSort().getOrderFor("orderDate").getDirection() == Sort.Direction.DESC
    }

    def "정렬에 id 가 이미 있으면 그대로 돌려준다"() {
        given:
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "id"))

        expect:
        PageableSupport.withStableSort(pageable).is(pageable)
    }

    def "unpaged 는 그대로 돌려준다"() {
        expect:
        PageableSupport.withStableSort(Pageable.unpaged()).isUnpaged()
    }
}

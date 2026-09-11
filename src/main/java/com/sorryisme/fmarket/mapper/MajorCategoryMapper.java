package com.sorryisme.fmarket.mapper;

import com.sorryisme.fmarket.dto.response.MajorCategoryResponse;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MajorCategoryMapper {

  List<MajorCategoryResponse> findMajorCategoryList();
}

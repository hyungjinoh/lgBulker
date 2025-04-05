package com.rayful.lgbulker.mapper;

import com.rayful.lgbulker.vo.EmailVo;
import com.rayful.lgbulker.vo.LGEmailVo;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface EmailMapper {

  List<LGEmailVo> findAll();

}

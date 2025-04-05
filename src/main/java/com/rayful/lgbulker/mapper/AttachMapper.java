package com.rayful.lgbulker.mapper;

import com.rayful.lgbulker.vo.AttachVo;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AttachMapper {

  List<AttachVo> findAll();

//
//  @Insert("INSERT INTO movies (title, director, release_date) VALUES (#{title}, #{director}, #{releaseDate})")
//  @Options(useGeneratedKeys = true, keyProperty = "id")
//  int insert(Movie movie);
//
//  @Update("UPDATE movies SET title = #{title}, director = #{director}, release_date = #{releaseDate} WHERE id = #{id}")
//  int update(Movie movie);
//
//  @Delete("DELETE FROM movies WHERE id = #{id}")
//  int delete(@Param("id") Long id);

}

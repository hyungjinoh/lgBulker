package com.rayful.lgbulker.mapper;

import com.rayful.lgbulker.entity.FileMetadata;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FileMetadataMapper {
  void insertFileMetadata(FileMetadata metadata);
}

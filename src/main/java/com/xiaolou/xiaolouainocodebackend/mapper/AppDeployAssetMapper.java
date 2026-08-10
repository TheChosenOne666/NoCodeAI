package com.xiaolou.xiaolouainocodebackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaolou.xiaolouainocodebackend.model.entity.AppDeployAsset;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 应用部署静态资源 Mapper，对应表 app_deploy_asset。
 */
@Mapper
public interface AppDeployAssetMapper extends BaseMapper<AppDeployAsset> {

    /**
     * 按部署标识与文件相对路径查询单条资源。
     *
     * @param deployKey 部署标识
     * @param filePath  文件相对路径（已规范化，不含前导 /）
     * @return 命中的资源，未命中返回 null
     */
    @Select("SELECT id, deploy_key AS deployKey, file_path AS filePath, content_type AS contentType, "
            + "file_size AS fileSize, content, create_time AS createTime, update_time AS updateTime, is_delete AS isDelete "
            + "FROM app_deploy_asset WHERE deploy_key = #{deployKey} AND file_path = #{filePath} AND is_delete = 0")
    AppDeployAsset selectByDeployKeyAndPath(@Param("deployKey") String deployKey, @Param("filePath") String filePath);

    /**
     * 批量 upsert 部署资源。利用唯一键 uk_deploy_path(deploy_key, file_path) 实现「存在即更新、不存在即插入」，
     * 避免重复生成同一应用时先删后插的竞态导致的 Duplicate entry 异常。
     *
     * @param assets 待写入资源列表（deploy_key + file_path 唯一确定一条）
     */
    @Insert("<script>"
            + "INSERT INTO app_deploy_asset (deploy_key, file_path, content_type, file_size, content, is_delete) VALUES "
            + "<foreach collection='list' item='item' separator=','>"
            + "(#{item.deployKey}, #{item.filePath}, #{item.contentType}, #{item.fileSize}, #{item.content}, #{item.isDelete})"
            + "</foreach>"
            + " ON DUPLICATE KEY UPDATE content_type = VALUES(content_type), file_size = VALUES(file_size), "
            + "content = VALUES(content), is_delete = VALUES(is_delete), update_time = CURRENT_TIMESTAMP"
            + "</script>")
    void upsertBatch(@Param("list") List<AppDeployAsset> assets);
}

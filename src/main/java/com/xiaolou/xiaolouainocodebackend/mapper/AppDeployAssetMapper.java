package com.xiaolou.xiaolouainocodebackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaolou.xiaolouainocodebackend.model.entity.AppDeployAsset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}

package com.xiaolou.xiaolouainocodebackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 应用部署静态资源实体，对应表 app_deploy_asset。
 * 存储已部署前端作品（如精品案例）的二进制文件，供生产环境直接查库返回。
 */
@TableName("app_deploy_asset")
public class AppDeployAsset {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 部署标识，对应 app.deployKey */
    @TableField("deploy_key")
    private String deployKey;

    /** 文件相对路径，如 index.html / assets/xxx.js */
    @TableField("file_path")
    private String filePath;

    /** MIME 类型 */
    @TableField("content_type")
    private String contentType;

    /** 文件字节数 */
    @TableField("file_size")
    private Long fileSize;

    /** 文件二进制内容 */
    @TableField("content")
    private byte[] content;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /** 逻辑删除 0-未删 1-已删 */
    @TableField("is_delete")
    private Integer isDelete;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDeployKey() {
        return deployKey;
    }

    public void setDeployKey(String deployKey) {
        this.deployKey = deployKey;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public Integer getIsDelete() {
        return isDelete;
    }

    public void setIsDelete(Integer isDelete) {
        this.isDelete = isDelete;
    }
}

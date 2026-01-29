package com.xiaolou.xiaolouainocodebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaolou.xiaolouainocodebackend.model.dto.chathistory.ChatHistoryQueryRequest;
import com.xiaolou.xiaolouainocodebackend.model.entity.ChatHistory;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaolou.xiaolouainocodebackend.model.entity.User;

import java.time.LocalDateTime;

/**
* @author l
* @description 针对表【chat_history(对话历史)】的数据库操作Service
* @createDate 2026-01-29 00:10:17
*/
public interface ChatHistoryService extends IService<ChatHistory> {

    /**
     * 新增对话历史
     *
     * @param appId
     * @param message
     * @param messageType
     * @param userId
     * @return
     */
    boolean addChatMessage(Long appId, String message, String messageType, Long userId);

    /**
     * 根据appId删除对话历史
     *
     * @param appId
     * @return
     */
    boolean deleteByAppId(Long appId);

    /**
     * 获取查询包装类
     *
     * @param chatHistoryQueryRequest
     * @return
     */
    QueryWrapper<ChatHistory> getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);

    /**
     * 游标查询对话历史
     *
     * @param appId
     * @param pageSize
     * @param lastCreateTime
     * @param loginUser
     * @return
     */
    Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize,
                                               LocalDateTime lastCreateTime,
                                               User loginUser);
}

package com.xiaolou.xiaolouainocodebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaolou.xiaolouainocodebackend.common.ErrorCode;
import com.xiaolou.xiaolouainocodebackend.constant.CommonConstant;
import com.xiaolou.xiaolouainocodebackend.constant.UserConstant;
import com.xiaolou.xiaolouainocodebackend.exception.ThrowUtils;
import com.xiaolou.xiaolouainocodebackend.model.dto.chathistory.ChatHistoryQueryRequest;
import com.xiaolou.xiaolouainocodebackend.model.entity.App;
import com.xiaolou.xiaolouainocodebackend.model.entity.ChatHistory;
import com.xiaolou.xiaolouainocodebackend.model.entity.User;
import com.xiaolou.xiaolouainocodebackend.model.enums.ChatHistoryMessageTypeEnum;
import com.xiaolou.xiaolouainocodebackend.service.AppService;
import com.xiaolou.xiaolouainocodebackend.service.ChatHistoryService;
import com.xiaolou.xiaolouainocodebackend.mapper.ChatHistoryMapper;
import com.xiaolou.xiaolouainocodebackend.utils.SqlUtils;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
* @author l
* @description 针对表【chat_history(对话历史)】的数据库操作Service实现
* @createDate 2026-01-29 00:10:17
*/
@Service
@Slf4j
public class ChatHistoryServiceImpl extends ServiceImpl<ChatHistoryMapper, ChatHistory>
    implements ChatHistoryService{

    @Resource
    @Lazy
    private AppService appService;

    @Override
    public boolean addChatMessage(Long appId, String message, String messageType, Long userId) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "消息内容不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(messageType), ErrorCode.PARAMS_ERROR, "消息类型不能为空");
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR, "用户ID不能为空");
        // 验证消息类型是否有效
        ChatHistoryMessageTypeEnum messageTypeEnum = ChatHistoryMessageTypeEnum.getEnumByValue(messageType);
        ThrowUtils.throwIf(messageTypeEnum == null, ErrorCode.PARAMS_ERROR, "不支持的消息类型: " + messageType);
        ChatHistory chatHistory = new ChatHistory();
        chatHistory.setMessage(message);
        chatHistory.setMessageType(messageType);
        chatHistory.setAppId(appId);
        chatHistory.setUserId(userId);
        return this.save(chatHistory);
    }

    @Override
    public boolean deleteByAppId(Long appId) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        QueryWrapper<ChatHistory> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("appId", appId);
        return this.remove(queryWrapper);
    }

    /**
     * 获取查询包装类
     *
     * @param chatHistoryQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<ChatHistory> getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest) {
        QueryWrapper<ChatHistory> queryWrapper = new QueryWrapper();
        if (chatHistoryQueryRequest == null) {
            return queryWrapper;
        }
        Long id = chatHistoryQueryRequest.getId();
        String message = chatHistoryQueryRequest.getMessage();
        String messageType = chatHistoryQueryRequest.getMessageType();
        Long appId = chatHistoryQueryRequest.getAppId();
        Long userId = chatHistoryQueryRequest.getUserId();
        LocalDateTime lastCreateTime = chatHistoryQueryRequest.getLastCreateTime();
        String sortField = chatHistoryQueryRequest.getSortField();
        String sortOrder = chatHistoryQueryRequest.getSortOrder();

        // 只在参数不为null时添加查询条件
        if (id != null) {
            queryWrapper.eq("id", id);
        }
        if (StrUtil.isNotBlank(message)) {
            queryWrapper.like("message", message);
        }
        if (StrUtil.isNotBlank(messageType)) {
            queryWrapper.eq("messageType", messageType);
        }
        if (appId != null) {
            queryWrapper.eq("appId", appId);
        }
        if (userId != null) {
            queryWrapper.eq("userId", userId);
        }

        // 游标查询逻辑 - 只使用 createTime 作为游标
        if (lastCreateTime != null) {
            queryWrapper.lt("createTime", lastCreateTime);
        }

        // 排序
        if (StrUtil.isNotBlank(sortField)) {
            queryWrapper.orderBy(SqlUtils.validSortField(sortField),
                    CommonConstant.SORT_ORDER_ASC.equals(sortOrder),
                    sortField);
        } else {
            // 默认按创建时间降序排列
            queryWrapper.orderByDesc("createTime");
        }
        return queryWrapper;
    }


    @Override
    public Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize,
                                                      LocalDateTime lastCreateTime,
                                                      User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        ThrowUtils.throwIf(pageSize <= 0 || pageSize > 50, ErrorCode.PARAMS_ERROR, "页面大小必须在1-50之间");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        // 验证权限：只有应用创建者和管理员可以查看
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        boolean isCreator = app.getUserId().equals(loginUser.getId());
        ThrowUtils.throwIf(!isAdmin && !isCreator, ErrorCode.NO_AUTH_ERROR, "无权查看该应用的对话历史");
        // 构建查询条件
        ChatHistoryQueryRequest queryRequest = new ChatHistoryQueryRequest();
        queryRequest.setAppId(appId);
        queryRequest.setLastCreateTime(lastCreateTime);
        QueryWrapper<ChatHistory> queryWrapper = this.getQueryWrapper(queryRequest);
        // 查询数据
        return this.page(Page.of(1, pageSize), queryWrapper);
    }

    /**
     * AI 历史消息回传给模型时的最大保留长度。
     * 完整生成的 HTML 可能长达数十 KB，若原样回传会导致上下文雪球式膨胀、
     * 单轮生成超过流式超时（120s）而失败。这里只保留摘要，并明确告知模型
     * 完整代码已保存，本轮应基于用户新需求重新输出完整 HTML。
     */
    private static final int AI_HISTORY_SUMMARY_MAX_LEN = 800;

    /**
     * 将完整 AI 消息压缩为回传模型的摘要。
     * 保留前 {@link #AI_HISTORY_SUMMARY_MAX_LEN} 个字符以便模型感知上轮产物形态，
     * 并追加指令要求其重新生成完整 HTML（而非基于截断片段续写）。
     *
     * @param fullAiMessage 完整 AI 历史消息
     * @return 精简后的摘要消息
     */
    String summarizeAiMessage(String fullAiMessage) {
        if (StrUtil.isBlank(fullAiMessage)) {
            return fullAiMessage;
        }
        String summary = fullAiMessage.length() > AI_HISTORY_SUMMARY_MAX_LEN
                ? fullAiMessage.substring(0, AI_HISTORY_SUMMARY_MAX_LEN) + "...(已截断)"
                : fullAiMessage;
        return "[上一轮已生成完整的 " + codeGenTypeOf(fullAiMessage)
                + " 并保存为 index.html，内容为上述片段摘要，非完整代码]\n"
                + summary + "\n"
                + "[指令：请基于用户本轮的新需求，重新输出【完整】的 HTML 代码（含全部结构与脚本），不要续写或依赖被截断的内容]";
    }

    /**
     * 根据消息内容粗略判断其代码类型，仅用于历史摘要的可读性说明。
     */
    String codeGenTypeOf(String message) {
        if (StrUtil.isBlank(message)) {
            return "代码";
        }
        if (message.contains("<!DOCTYPE html") || message.contains("<html")) {
            return "HTML 页面";
        }
        return "代码";
    }

    @Override
    public int loadChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory, int maxCount) {
        try {
            // 直接构造查询条件，起始点为 1 而不是 0，用于排除最新的用户消息
            LambdaQueryWrapper<ChatHistory> lambdaQueryWrapper = new LambdaQueryWrapper<>();
            lambdaQueryWrapper.eq(ChatHistory::getAppId, appId)
                    .orderByDesc(ChatHistory::getCreateTime)
                    .last("LIMIT " + 1 + ", " + maxCount);

            List<ChatHistory> historyList = this.list(lambdaQueryWrapper);
            if (CollUtil.isEmpty(historyList)) {
                return 0;
            }
            // 反转列表，确保按时间正序（老的在前，新的在后）
            historyList = historyList.reversed();
            // 按时间顺序添加到记忆中
            int loadedCount = 0;
            // 先清理历史缓存，防止重复加载
            chatMemory.clear();
            for (ChatHistory history : historyList) {
                if (ChatHistoryMessageTypeEnum.USER.getValue().equals(history.getMessageType())) {
                    // 用户指令必须原样保留，否则模型无法感知要修改的需求
                    chatMemory.add(UserMessage.from(history.getMessage()));
                    loadedCount++;
                } else if (ChatHistoryMessageTypeEnum.AI.getValue().equals(history.getMessageType())) {
                    // AI 历史消息精简后回传，避免完整 HTML 造成上下文膨胀与超时
                    chatMemory.add(AiMessage.from(summarizeAiMessage(history.getMessage())));
                    loadedCount++;
                }
            }
            log.info("成功为 appId: {} 加载了 {} 条历史对话（AI 消息已摘要压缩）", appId, loadedCount);
            return loadedCount;
        } catch (Exception e) {
            log.error("加载历史对话失败，appId: {}, error: {}", appId, e.getMessage(), e);
            // 加载失败不影响系统运行，只是没有历史上下文
            return 0;
        }
    }


}





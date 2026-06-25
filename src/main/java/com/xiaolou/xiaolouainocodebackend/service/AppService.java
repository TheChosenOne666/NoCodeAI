package com.xiaolou.xiaolouainocodebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xiaolou.xiaolouainocodebackend.model.dto.app.AppAddRequest;
import com.xiaolou.xiaolouainocodebackend.model.dto.app.AppQueryRequest;
import com.xiaolou.xiaolouainocodebackend.model.dto.codegen.CodeGenStreamEvent;
import com.xiaolou.xiaolouainocodebackend.model.entity.App;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaolou.xiaolouainocodebackend.model.entity.User;
import com.xiaolou.xiaolouainocodebackend.model.vo.AppVO;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;

/**
* @author l
* @description 针对表【app(应用)】的数据库操作Service
* @createDate 2026-01-27 17:21:55
*/
public interface AppService extends IService<App> {

    /**
     * 创建应用
     *
     * @param appAddRequest 应用创建请求
     * @param loginUser     当前登录用户
     * @return 应用ID
     */
    Long createApp(AppAddRequest appAddRequest, User loginUser);

    /**
     * 查询app关联信息服务
     * @param app
     * @return
     */
    AppVO getAppVO(App app);

    /**
     * 获取查询条件
     * @param appQueryRequest
     * @return
     */
    QueryWrapper<App> getQueryWrapper(AppQueryRequest appQueryRequest);

    /**
     * 获取app关联信息列表(先收集到id再拿到key和value最后返回查出的value)
     * @param appList
     * @return
     */
    List<AppVO> getAppVOList(List<App> appList);

    /**
     * 聊天生成代码
     *
     * @param appId
     * @param message
     * @param requestId 请求ID，用于与右侧代码实时展示SSE共享同一次AI生成
     * @param loginUser
     * @return
     */
    Flux<String> chatToGenCode(Long appId, String message, String requestId, User loginUser);

    /**
     * 获取 Vue 项目代码生成实时展示流（右侧代码预览专用）
     *
     * @param appId     应用ID
     * @param message   用户消息
     * @param requestId 请求ID，用于与左侧对话SSE共享同一次AI生成
     * @param loginUser 当前登录用户
     * @return 结构化 SSE 事件流
     */
    Flux<ServerSentEvent<CodeGenStreamEvent>> getVueProjectGenStreamDetail(Long appId, String message, String requestId, User loginUser);

    /**
     * 部署应用
     *
     * @param appId
     * @param loginUser
     * @return
     */
    String deployApp(Long appId, User loginUser);

    /**
     * 异步生成应用截图并更新封面
     *
     * @param appId  应用ID
     * @param appUrl 应用访问URL
     */
    void generateAppScreenshotAsync(Long appId, String appUrl);
}

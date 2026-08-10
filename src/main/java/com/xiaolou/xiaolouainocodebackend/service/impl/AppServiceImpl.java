package com.xiaolou.xiaolouainocodebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaolou.xiaolouainocodebackend.ai.AiCodeGenTypeRoutingService;
import com.xiaolou.xiaolouainocodebackend.ai.AiCodeGenTypeRoutingServiceFactory;
import com.xiaolou.xiaolouainocodebackend.common.ErrorCode;
import com.xiaolou.xiaolouainocodebackend.constant.AppConstant;
import com.xiaolou.xiaolouainocodebackend.constant.CommonConstant;
import com.xiaolou.xiaolouainocodebackend.core.AiCodeGeneratorFacade;
import com.xiaolou.xiaolouainocodebackend.core.builder.VueProjectBuilder;
import com.xiaolou.xiaolouainocodebackend.core.handler.StreamHandlerExecutor;
import com.xiaolou.xiaolouainocodebackend.exception.BusinessException;
import com.xiaolou.xiaolouainocodebackend.exception.ThrowUtils;
import com.xiaolou.xiaolouainocodebackend.model.dto.app.AppAddRequest;
import com.xiaolou.xiaolouainocodebackend.model.dto.app.AppQueryRequest;
import com.xiaolou.xiaolouainocodebackend.model.dto.codegen.CodeGenStreamEvent;
import com.xiaolou.xiaolouainocodebackend.model.entity.App;
import com.xiaolou.xiaolouainocodebackend.model.entity.AppDeployAsset;
import org.springframework.http.codec.ServerSentEvent;
import com.xiaolou.xiaolouainocodebackend.model.entity.User;
import com.xiaolou.xiaolouainocodebackend.model.enums.ChatHistoryMessageTypeEnum;
import com.xiaolou.xiaolouainocodebackend.model.enums.CodeGenTypeEnum;
import com.xiaolou.xiaolouainocodebackend.model.vo.AppVO;
import com.xiaolou.xiaolouainocodebackend.model.vo.UserVO;
import com.xiaolou.xiaolouainocodebackend.service.AppService;
import com.xiaolou.xiaolouainocodebackend.mapper.AppDeployAssetMapper;
import com.xiaolou.xiaolouainocodebackend.mapper.AppMapper;
import com.xiaolou.xiaolouainocodebackend.service.ChatHistoryService;
import com.xiaolou.xiaolouainocodebackend.service.ScreenshotService;
import com.xiaolou.xiaolouainocodebackend.service.UserService;
import com.xiaolou.xiaolouainocodebackend.utils.SqlUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
* @author l
* @description 针对表【app(应用)】的数据库操作Service实现
* @createDate 2026-01-27 17:21:55
*/
@Service
@Slf4j
public class AppServiceImpl extends ServiceImpl<AppMapper, App>
    implements AppService{

    @Resource
    private UserService userService;

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private StreamHandlerExecutor streamHandlerExecutor;

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    @Resource
    private ScreenshotService screenshotService;

    @Resource
    private AppDeployAssetMapper appDeployAssetMapper;

    @Resource
    private AiCodeGenTypeRoutingServiceFactory aiCodeGenTypeRoutingServiceFactory;

    @Override
    public Long createApp(AppAddRequest appAddRequest, User loginUser) {
        // 参数校验
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "初始化 prompt 不能为空");
        // 构造入库对象
        App app = new App();
        BeanUtil.copyProperties(appAddRequest, app);
        app.setUserId(loginUser.getId());
        // 应用名称暂时为 initPrompt 前 12 位
        app.setAppName(initPrompt.substring(0, Math.min(initPrompt.length(), 12)));
        // 使用 AI 智能选择代码生成类型（多例）
        AiCodeGenTypeRoutingService routingService = aiCodeGenTypeRoutingServiceFactory.createAiCodeGenTypeRoutingService();
        CodeGenTypeEnum selectedCodeGenType = routingService.routeCodeGenType(initPrompt);
        app.setCodeGenType(selectedCodeGenType.getValue());
        // 插入数据库
        boolean result = this.save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        log.info("应用创建成功，ID: {}, 类型: {}", app.getId(), selectedCodeGenType.getValue());
        return app.getId();
    }


    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);
        // 关联查询用户信息
        Long userId = app.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            appVO.setUser(userVO);
        }
        return appVO;
    }
    @Override
    public QueryWrapper<App> getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        QueryWrapper<App> queryWrapper = new QueryWrapper<>();

        // 只在参数不为null时才添加查询条件
        if (id != null) {
            queryWrapper.eq("id", id);
        }
        if (StrUtil.isNotBlank(appName)) {
            queryWrapper.like("appName", appName);
        }
        if (StrUtil.isNotBlank(cover)) {
            queryWrapper.like("cover", cover);
        }
        if (StrUtil.isNotBlank(initPrompt)) {
            queryWrapper.like("initPrompt", initPrompt);
        }
        if (StrUtil.isNotBlank(codeGenType)) {
            queryWrapper.eq("codeGenType", codeGenType);
        }
        if (StrUtil.isNotBlank(deployKey)) {
            queryWrapper.eq("deployKey", deployKey);
        }
        if (priority != null) {
            queryWrapper.eq("priority", priority);
        }
        if (userId != null) {
            queryWrapper.eq("userId", userId);
        }
        if (SqlUtils.validSortField(sortField)) {
            queryWrapper.orderBy(true, sortOrder.equals(CommonConstant.SORT_ORDER_ASC), sortField);
        } else {
            // 默认按创建时间倒序，最新在最上方
            queryWrapper.orderByDesc("createTime");
        }

        return queryWrapper;
    }
    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        // 批量获取用户信息，避免 N+1 查询问题
        Set<Long> userIds = appList.stream()
                .map(App::getUserId)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO));
        return appList.stream().map(app -> {
            AppVO appVO = getAppVO(app);
            UserVO userVO = userVOMap.get(app.getUserId());
            appVO.setUser(userVO);
            return appVO;
        }).collect(Collectors.toList());
    }

    @Override
    public Flux<ServerSentEvent<String>> chatToGenCode(Long appId, String message, String requestId, User loginUser) {
        // 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "用户消息不能为空");
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.PARAMS_ERROR, "应用不存在");
        // 验证用户是否有权限访问该应用，仅本人可生成代码
        if(!Objects.equals(app.getUserId(), loginUser.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "无权限访问该应用");
        }
        // 获取应用的代码生成类型
        String codeGenTypeStr = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenTypeStr);
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的应用代码生成类型");
        }
        // 5. 通过校验后，添加用户消息到对话历史
        chatHistoryService.addChatMessage(appId, message, ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());
        // 6. 调用 AI 生成代码（流式），返回 SSE 事件流（含 chunk/done/business-error）
        Flux<ServerSentEvent<String>> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(message, codeGenTypeEnum, appId, requestId);
        // 7. 收集 AI 响应内容并在完成后记录到对话历史
        return streamHandlerExecutor.doExecute(codeStream, chatHistoryService, appId, loginUser, codeGenTypeEnum);

    }

    @Override
    public Flux<ServerSentEvent<CodeGenStreamEvent>> getVueProjectGenStreamDetail(Long appId, String message, String requestId, User loginUser) {
        // 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "用户消息不能为空");
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.PARAMS_ERROR, "应用不存在");
        // 验证用户是否有权限访问该应用，仅本人可生成代码
        if (!Objects.equals(app.getUserId(), loginUser.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "无权限访问该应用");
        }
        // 仅 Vue 项目支持
        String codeGenTypeStr = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenTypeStr);
        if (codeGenTypeEnum != CodeGenTypeEnum.VUE_PROJECT) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "仅 Vue 项目支持代码实时展示流");
        }
        // 调用 AI 生成代码结构化实时流
        return aiCodeGeneratorFacade.generateVueProjectStreamDetail(message, appId, requestId);
    }

    @Override
    public String deployApp(Long appId, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 验证用户是否有权限部署该应用，仅本人可以部署
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限部署该应用");
        }
        // 4. 检查是否已有 deployKey
        String deployKey = app.getDeployKey();
        // 没有则生成 6 位 deployKey（大小写字母 + 数字）
        if (StrUtil.isBlank(deployKey)) {
            deployKey = RandomUtil.randomString(6);
        }
        // 5. 获取代码生成类型，构建源目录路径
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        // 6. 检查源目录是否存在；不存在时尝试从数据库中的 preview_{appId} 资源复制，
        //    以兼容 Railway 等无状态容器磁盘目录被清理后仍能部署（预览资源已持久化）。
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            log.warn("本地部署源目录不存在，尝试从数据库预览资源复制，appId={}, sourceDir={}", appId, sourceDirPath);
            boolean copied = copyPreviewAssetsToDeploy(appId, deployKey);
            if (!copied) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码不存在，请先生成代码");
            }
            log.info("已从数据库预览资源复制到正式部署，appId={}, deployKey={}", appId, deployKey);
        } else {
            // 7. Vue 项目特殊处理：执行构建
            CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
            if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
                // Vue 项目需要构建
                boolean buildSuccess = vueProjectBuilder.buildProject(sourceDirPath);
                ThrowUtils.throwIf(!buildSuccess, ErrorCode.SYSTEM_ERROR, "Vue 项目构建失败，请检查代码和依赖");
                // 检查 dist 目录是否存在
                File distDir = new File(sourceDirPath, "dist");
                ThrowUtils.throwIf(!distDir.exists(), ErrorCode.SYSTEM_ERROR, "Vue 项目构建完成但未生成 dist 目录");
                // 将 dist 目录作为部署源
                sourceDir = distDir;
                log.info("Vue 项目构建成功，将部署 dist 目录: {}", distDir.getAbsolutePath());
            }
            // 8. 将部署文件写入数据库 app_deploy_asset（与精品案例一致，访问时由 StaticResourceController 查库返回，
            //    不再依赖本地磁盘，线上环境稳定且 URL 不绑定 localhost）
            try {
                saveDeployAssets(deployKey, sourceDir);
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "部署失败：" + e.getMessage());
            }
        }
        // 9. 更新应用的 deployKey 和部署时间
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用部署信息失败");
        // 10. 返回可访问的 URL（与 StaticResourceController 路由 /api/static/{deployKey}/ 对齐）
        String appDeployUrl = String.format("%s/static/%s/", AppConstant.CODE_DEPLOY_HOST, deployKey);
        generateAppScreenshotAsync(appId, appDeployUrl);
        return appDeployUrl;
    }

    /**
     * 将部署源目录下的所有文件写入 app_deploy_asset 表（与精品案例一致）。
     * 访问时由 StaticResourceController 查库返回，不依赖本地磁盘。
     *
     * @param deployKey 部署标识
     * @param sourceDir 部署源目录（HTML 为项目根目录，Vue 为 dist 目录）
     */
    private void saveDeployAssets(String deployKey, File sourceDir) {
        List<AppDeployAsset> assets = new ArrayList<>();
        collectDeployAssets(deployKey, sourceDir, sourceDir, assets);
        ThrowUtils.throwIf(assets.isEmpty(), ErrorCode.SYSTEM_ERROR, "部署内容为空，未找到可部署文件");
        // 批量 upsert：依赖唯一键 uk_deploy_path(deploy_key, file_path) 实现存在即更新、不存在即插入，
        // 避免重复部署场景下的 Duplicate entry 异常
        appDeployAssetMapper.upsertBatch(assets);
        log.info("部署资源已写入数据库，deployKey={}，文件数={}", deployKey, assets.size());
    }

    /**
     * 将应用生成产物持久化到数据库，作为「未部署时的预览资源」。
     * 使用 deployKey = preview_{appId}，前端未部署时通过 /api/static/preview_{appId}/ 访问，
     * 避免因 Railway 等无状态容器重建导致临时目录丢失而预览 404。
     *
     * @param appId     应用ID
     * @param sourceDir 生成产物目录（HTML 为项目根目录，Vue 为 dist 目录）
     */
    @Override
    public void savePreviewAssets(Long appId, File sourceDir) {
        String deployKey = "preview_" + appId;
        List<AppDeployAsset> assets = new ArrayList<>();
        collectDeployAssets(deployKey, sourceDir, sourceDir, assets);
        if (assets.isEmpty()) {
            log.warn("预览资源为空，跳过写入，appId={}", appId);
            return;
        }
        // 批量 upsert：依赖唯一键 uk_deploy_path(deploy_key, file_path) 实现存在即更新、不存在即插入，
        // 避免重复生成同一应用时先删后插竞态导致的 Duplicate entry 异常
        appDeployAssetMapper.upsertBatch(assets);
        log.info("预览资源已持久化到数据库，deployKey={}，文件数={}", deployKey, assets.size());
    }

    /**
     * 将数据库中 preview_{appId} 的预览资源复制到正式 deployKey，用于无状态容器
     * 本地磁盘目录已被清理、但数据库预览资源仍存在的场景。
     *
     * @param appId     应用ID
     * @param deployKey 正式部署标识
     * @return true-复制成功且至少有一个文件；false-预览资源不存在或为空
     */
    private boolean copyPreviewAssetsToDeploy(Long appId, String deployKey) {
        String previewKey = "preview_" + appId;
        List<AppDeployAsset> previewAssets = appDeployAssetMapper.selectListByDeployKey(previewKey);
        if (CollUtil.isEmpty(previewAssets)) {
            log.warn("数据库中不存在预览资源，无法复制到部署，appId={}", appId);
            return false;
        }
        List<AppDeployAsset> newAssets = previewAssets.stream()
                .map(asset -> {
                    AppDeployAsset copy = new AppDeployAsset();
                    copy.setDeployKey(deployKey);
                    copy.setFilePath(asset.getFilePath());
                    copy.setContentType(asset.getContentType());
                    copy.setFileSize(asset.getFileSize());
                    copy.setContent(asset.getContent());
                    copy.setIsDelete(0);
                    return copy;
                })
                .collect(Collectors.toList());
        appDeployAssetMapper.upsertBatch(newAssets);
        log.info("已将 preview 资源复制到正式部署，previewKey={}，deployKey={}，文件数={}",
                previewKey, deployKey, newAssets.size());
        return true;
    }

    /**
     * 递归收集部署文件，生成 AppDeployAsset 列表。
     *
     * @param deployKey    部署标识
     * @param rootDir      源根目录（用于计算相对路径）
     * @param current      当前遍历目录
     * @param assets       收集结果
     */
    private void collectDeployAssets(String deployKey, File rootDir, File current, List<AppDeployAsset> assets) {
        File[] children = current.listFiles();
        if (children == null) {
            return;
        }
        for (File file : children) {
            if (file.isDirectory()) {
                collectDeployAssets(deployKey, rootDir, file, assets);
                continue;
            }
            String relativePath = rootDir.toURI().relativize(file.toURI()).getPath();
            if (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }
            try {
                byte[] content = Files.readAllBytes(file.toPath());
                String contentType = Files.probeContentType(file.toPath());
                if (StrUtil.isBlank(contentType)) {
                    contentType = "application/octet-stream";
                }
                AppDeployAsset asset = new AppDeployAsset();
                asset.setDeployKey(deployKey);
                asset.setFilePath(relativePath);
                asset.setContentType(contentType);
                asset.setFileSize((long) content.length);
                asset.setContent(content);
                asset.setIsDelete(0);
                assets.add(asset);
            } catch (Exception e) {
                log.error("读取部署文件失败：{}", file.getAbsolutePath(), e);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "读取部署文件失败：" + file.getName());
            }
        }
    }

    /**
     * 异步生成应用截图并更新封面
     *
     * @param appId  应用ID
     * @param appUrl 应用访问URL
     */
    @Override
    public void generateAppScreenshotAsync(Long appId, String appUrl) {
        // 使用虚拟线程异步执行
        Thread.startVirtualThread(() -> {
            // 调用截图服务生成截图并上传
            String screenshotUrl = screenshotService.generateAndUploadScreenshot(appUrl);
            // 更新应用封面字段
            App updateApp = new App();
            updateApp.setId(appId);
            updateApp.setCover(screenshotUrl);
            boolean updated = this.updateById(updateApp);
            ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "更新应用封面字段失败");
        });
    }


    /**
     * 删除应用时关联删除对话历史
     *
     * @param id 应用ID
     * @return 是否成功
     */
    @Override
    public boolean removeById(Serializable id) {
        if (id == null) {
            return false;
        }
        // 转换为 Long 类型
        long appId = Long.parseLong(id.toString());
        if (appId <= 0) {
            return false;
        }
        // 先删除关联的对话历史
        try {
            chatHistoryService.deleteByAppId(appId);
        } catch (Exception e) {
            // 记录日志但不阻止应用删除
            log.error("删除应用关联的对话历史失败: {}", e);
        }
        // 删除应用
        return super.removeById(id);
    }

}





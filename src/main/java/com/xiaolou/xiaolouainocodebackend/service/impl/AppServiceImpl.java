package com.xiaolou.xiaolouainocodebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaolou.xiaolouainocodebackend.common.ErrorCode;
import com.xiaolou.xiaolouainocodebackend.constant.CommonConstant;
import com.xiaolou.xiaolouainocodebackend.exception.BusinessException;
import com.xiaolou.xiaolouainocodebackend.model.dto.app.AppQueryRequest;
import com.xiaolou.xiaolouainocodebackend.model.entity.App;
import com.xiaolou.xiaolouainocodebackend.model.entity.User;
import com.xiaolou.xiaolouainocodebackend.model.vo.AppVO;
import com.xiaolou.xiaolouainocodebackend.model.vo.UserVO;
import com.xiaolou.xiaolouainocodebackend.service.AppService;
import com.xiaolou.xiaolouainocodebackend.mapper.AppMapper;
import com.xiaolou.xiaolouainocodebackend.service.UserService;
import com.xiaolou.xiaolouainocodebackend.utils.SqlUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
* @author l
* @description 针对表【app(应用)】的数据库操作Service实现
* @createDate 2026-01-27 17:21:55
*/
@Service
public class AppServiceImpl extends ServiceImpl<AppMapper, App>
    implements AppService{

    @Resource
    private UserService userService;

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
        return queryWrapper
                .eq("id", id)
                .like("appName", appName)
                .like("cover", cover)
                .like("initPrompt", initPrompt)
                .eq("codeGenType", codeGenType)
                .eq("deployKey", deployKey)
                .eq("priority", priority)
                .eq("userId", userId)
                .orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC), sortField);
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
}





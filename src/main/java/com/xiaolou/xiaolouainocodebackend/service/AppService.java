package com.xiaolou.xiaolouainocodebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xiaolou.xiaolouainocodebackend.model.dto.app.AppQueryRequest;
import com.xiaolou.xiaolouainocodebackend.model.entity.App;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaolou.xiaolouainocodebackend.model.vo.AppVO;

import java.util.List;

/**
* @author l
* @description 针对表【app(应用)】的数据库操作Service
* @createDate 2026-01-27 17:21:55
*/
public interface AppService extends IService<App> {

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
}

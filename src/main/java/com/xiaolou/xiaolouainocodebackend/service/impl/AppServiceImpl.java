package com.xiaolou.xiaolouainocodebackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaolou.xiaolouainocodebackend.model.entity.App;
import com.xiaolou.xiaolouainocodebackend.service.AppService;
import com.xiaolou.xiaolouainocodebackend.mapper.AppMapper;
import org.springframework.stereotype.Service;

/**
* @author l
* @description 针对表【app(应用)】的数据库操作Service实现
* @createDate 2026-01-27 17:21:55
*/
@Service
public class AppServiceImpl extends ServiceImpl<AppMapper, App>
    implements AppService{

}





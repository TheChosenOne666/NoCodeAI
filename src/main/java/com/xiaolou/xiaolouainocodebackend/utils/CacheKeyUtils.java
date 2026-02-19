package com.xiaolou.xiaolouainocodebackend.utils;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;

/**
 * 缓存key生成工具类
 */
public class CacheKeyUtils {
    /**
     * 根据对象生成key（JSON + MD5）
     *
     * @param obj
     * @return
     */
    public static String generateKey(Object obj){
        if (obj == null){
            return DigestUtil.md5Hex("null");
        }
        // 将对象转为JSON字符串
        String jsonStr = JSONUtil.toJsonStr(obj);
        return DigestUtil.md5Hex(jsonStr);
    }
}

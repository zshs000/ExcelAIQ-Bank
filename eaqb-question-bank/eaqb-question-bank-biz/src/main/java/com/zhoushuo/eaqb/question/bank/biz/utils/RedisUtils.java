package com.zhoushuo.eaqb.question.bank.biz.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis工具类，简化Redis操作
 */
@Component
@Slf4j
public class RedisUtils {

    @Autowired
    public RedisTemplate<String, Object> redisTemplate;

    /**
     * 设置缓存
     * @param key 键
     * @param value 值
     */
    public void set(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, value);
        } catch (Exception e) {
            log.error("Redis set operation failed, key: {}", key, e);
        }
    }

    /**
     * 设置缓存并指定过期时间
     * @param key 键
     * @param value 值
     * @param expire 过期时间
     * @param timeUnit 时间单位
     */
    public void set(String key, Object value, long expire, TimeUnit timeUnit) {
        try {
            redisTemplate.opsForValue().set(key, value, expire, timeUnit);
        } catch (Exception e) {
            log.error("Redis set with expire operation failed, key: {}", key, e);
        }
    }

    /**
     * 获取缓存
     * @param key 键
     * @return 值
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        try {
            return (T) redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("Redis get operation failed, key: {}", key, e);
            return null;
        }
    }

    /**
     * 删除缓存
     * @param key 键
     * @return 是否成功
     */
    public boolean delete(String key) {
        try {
            return redisTemplate.delete(key);
        } catch (Exception e) {
            log.error("Redis delete operation failed, key: {}", key, e);
            return false;
        }
    }

    /**
     * 批量删除缓存
     * @param keys 键集合
     * @return 删除的数量
     */
    public long delete(Collection<String> keys) {
        try {
            return redisTemplate.delete(keys);
        } catch (Exception e) {
            log.error("Redis batch delete operation failed", e);
            return 0;
        }
    }

    /**
     * 设置过期时间
     * @param key 键
     * @param expire 过期时间
     * @param timeUnit 时间单位
     * @return 是否成功
     */
    public boolean expire(String key, long expire, TimeUnit timeUnit) {
        try {
            return redisTemplate.expire(key, expire, timeUnit);
        } catch (Exception e) {
            log.error("Redis expire operation failed, key: {}", key, e);
            return false;
        }
    }

    /**
     * 判断键是否存在
     * @param key 键
     * @return 是否存在
     */
    public boolean hasKey(String key) {
        try {
            return redisTemplate.hasKey(key);
        } catch (Exception e) {
            log.error("Redis hasKey operation failed, key: {}", key, e);
            return false;
        }
    }

    /**
     * 获取过期时间
     * @param key 键
     * @param timeUnit 时间单位
     * @return 过期时间
     */
    public long getExpire(String key, TimeUnit timeUnit) {
        try {
            return redisTemplate.getExpire(key, timeUnit);
        } catch (Exception e) {
            log.error("Redis getExpire operation failed, key: {}", key, e);
            return -1;
        }
    }

    // Hash 相关操作
    
    /**
     * 设置Hash值
     * @param key 键
     * @param hashKey Hash键
     * @param value 值
     */
    public void hSet(String key, String hashKey, Object value) {
        try {
            redisTemplate.opsForHash().put(key, hashKey, value);
        } catch (Exception e) {
            log.error("Redis hSet operation failed, key: {}, hashKey: {}", key, hashKey, e);
        }
    }

    /**
     * 批量设置Hash值
     * @param key 键
     * @param map 键值对
     */
    public <T> void hPutAll(String key, Map<String, T> map) {
        try {
            // 创建一个新的Map<Object, Object>用于Redis操作
            Map<Object, Object> objectMap = new HashMap<>();
            for (Map.Entry<String, T> entry : map.entrySet()) {
                objectMap.put(entry.getKey(), entry.getValue());
            }
            redisTemplate.opsForHash().putAll(key, objectMap);
        } catch (Exception e) {
            log.error("Redis hPutAll operation failed, key: {}", key, e);
        }
    }

    /**
     * 获取Hash值
     * @param key 键
     * @param hashKey Hash键
     * @return 值
     */
    @SuppressWarnings("unchecked")
    public <T> T hGet(String key, String hashKey) {
        try {
            return (T) redisTemplate.opsForHash().get(key, hashKey);
        } catch (Exception e) {
            log.error("Redis hGet operation failed, key: {}, hashKey: {}", key, hashKey, e);
            return null;
        }
    }

    /**
     * 获取Hash的所有键值对
     * @param key 键
     * @return 键值对
     */
    @SuppressWarnings("unchecked")
    public <T> Map<String, T> hGetAll(String key) {
        try {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
            if (entries == null || entries.isEmpty()) {
                return null;
            }
            // 创建一个新的Map并进行类型转换
            Map<String, T> resultMap = new HashMap<>();
            for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                resultMap.put(String.valueOf(entry.getKey()), (T) entry.getValue());
            }
            return resultMap;
        } catch (Exception e) {
            log.error("Redis hGetAll operation failed, key: {}", key, e);
            return null;
        }
    }

    /**
     * 获取Hash的所有键
     * @param key 键
     * @return 键集合
     */
    public Set<Object> hKeys(String key) {
        try {
            return redisTemplate.opsForHash().keys(key);
        } catch (Exception e) {
            log.error("Redis hKeys operation failed, key: {}", key, e);
            return null;
        }
    }

    /**
     * 删除Hash中的某个值
     * @param key 键
     * @param hashKey Hash键
     */
    public void hDelete(String key, String hashKey) {
        try {
            redisTemplate.opsForHash().delete(key, hashKey);
        } catch (Exception e) {
            log.error("Redis hDelete operation failed, key: {}, hashKey: {}", key, hashKey, e);
        }
    }

    /**
     * 判断Hash中是否存在某个键
     * @param key 键
     * @param hashKey Hash键
     * @return 是否存在
     */
    public boolean hHasKey(String key, String hashKey) {
        try {
            return redisTemplate.opsForHash().hasKey(key, hashKey);
        } catch (Exception e) {
            log.error("Redis hHasKey operation failed, key: {}, hashKey: {}", key, hashKey, e);
            return false;
        }
    }
}
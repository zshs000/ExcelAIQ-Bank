-- KEYS: 1 验证码，2 手机号日计数，3 IP 日计数（没有 IP 时省略）。
-- ARGV: 1 JSON 序列化后的验证码，2 手机号限额，3 IP 限额，
--       4 验证码有效期（毫秒），5 JVM 当天结束时间（Unix 毫秒）。
-- 返回: 1 成功，-1 手机号超额，-2 IP 超额，-3 已有验证码。
-- 所有 key 必须由同一 Redis 主节点处理；Cluster 部署还要求同槽。

local function readCount(key)
    local raw = redis.call('GET', key)
    if not raw then
        return 0
    end
    local count = tonumber(raw)
    -- 在首次写入前检查计数格式，避免后续 INCR 报错留下部分写入。
    if not count or count < 0 or count % 1 ~= 0 or tostring(count) ~= raw then
        error('Invalid verification code daily count')
    end
    return count
end

-- 保持原来的错误优先级：手机号额度 -> IP 额度 -> 冷却。
if readCount(KEYS[2]) >= tonumber(ARGV[2]) then
    return -1
end
if KEYS[3] and readCount(KEYS[3]) >= tonumber(ARGV[3]) then
    return -2
end

-- 额度检查通过后才尝试创建验证码。重复申请不修改验证码，也不扣额度。
if not redis.call('SET', KEYS[1], ARGV[1], 'NX', 'PX', ARGV[4]) then
    return -3
end

local phoneCount = redis.call('INCR', KEYS[2])
if phoneCount == 1 then
    redis.call('PEXPIREAT', KEYS[2], ARGV[5])
end
if KEYS[3] then
    local ipCount = redis.call('INCR', KEYS[3])
    if ipCount == 1 then
        redis.call('PEXPIREAT', KEYS[3], ARGV[5])
    end
end
return 1

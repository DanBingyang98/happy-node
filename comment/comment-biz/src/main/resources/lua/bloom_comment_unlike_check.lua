local key = KEYS[1] -- 操作的 Redis Key
local commentId = ARGV[1] -- 评论ID

-- 使用 EXISTS 命令检查布隆过滤器是否存在
local exists = redis.call('EXISTS', key)
if exists == 0 then
    return -1
end

return redis.call('BF.EXISTS', key, commentId)
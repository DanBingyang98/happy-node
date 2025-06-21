-- LUA 脚本：笔记收藏布隆过滤器
local key = KEYS[1] -- 操作的 Redis Key
local noteId = ARGV[1] -- 笔记ID

-- 使用 EXISTS 命令检查布隆过滤器是否存在
local exists = redis.call('EXISTS', key)
if exists == 0 then
    return -1
end

-- 使用 BF.EXISTS 命令检查布隆过滤器是否存在该元素
local isCollected = redis.call('BF.EXISTS', key, noteId)
if isCollected == 1 then
    return 1
end

-- 未被收藏，添加收藏数据
redis.call('BF.EXISTS', key, noteId)
return 0
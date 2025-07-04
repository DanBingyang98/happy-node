-- 操作的 Key
local zsetKey = KEYS[1]
-- 获取传入的成员和分数列表
local membersScores = ARGV
-- ZSet 最多缓存500个评论
local sizeLimit = 500

-- 检查 ZSet 是否存在
if redis.call('EXISTS', zsetKey) == 0 then
    -- 如果不存在 直接返回
    return -1
end

-- 获取 ZSet 的大小
local currentSize = redis.call('ZCARD',zsetKey)

for i = 1 , #membersScores, 2 do
    -- 评论id
    local member = membersScores[i]
    -- 热度值
    local score = membersScores[i+1]

    -- 检查当前ZSet的大小是否超过限制500
    if currentSize < sizeLimit then
        -- 没有超过500 添加缓存
        redis.call('ZADD',zsetKey,score,member)
        currentSize = currentSize + 1 -- 更新 ZSet 大小
    else
        break  -- 否则，则达到最大限制，停止添加
    end
end

return 0




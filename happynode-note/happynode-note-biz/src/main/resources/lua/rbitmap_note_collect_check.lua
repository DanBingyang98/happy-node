-- LUA 脚本：笔记收藏rbitmap
local key = KEYS[1] -- 操作的 Redis Key
local noteId = ARGV[1] -- 笔记ID

local exists = redis.call('EXISTS', key)
if exists == 0 then
    return -1
end

local isCollected = redis.call('R.GETBIT', key, noteId)
if isCollected == 1 then
    return 1
end

redis.call('R.SETBIT', key, noteId, 1)
return 0
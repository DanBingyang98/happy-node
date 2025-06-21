-- 操作的 Key
local key = KEYS[1]

-- bloom中添加笔记id
for i = 1, #ARGV - 1 do
  redis.call('BF.ADD', key, ARGV[i])
end

-- 最后一个参数是过期时间
local expireTime  = ARGV[#ARGV]
-- 设置过期时间
redis.call('EXPIRE', key, expireTime )
reutrn 0

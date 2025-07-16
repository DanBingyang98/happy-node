local key = KEY[1]
local noteId = ARGV[1]
local expireTime = ARGV[2]

redis.call('R.SETBIT', key, noteId, 1)
redis.call('EXPIRE', key, expireTime)
return 0

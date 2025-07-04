private void syncHeatComments2Redis(String countCommentTotalKey, Long noteId) {
    List<CommentDO> commentDOS = commentDOMapper.selectHeatComments(noteId);
    if (CollUtil.isNotEmpty(commentDOS)) {
        redisTemplate.executePipelined((RedisCallback<?>) connection -> {
            // 先判断 key 是否存在
            Boolean exists = redisTemplate.hasKey(countCommentTotalKey);
            if (exists != null && exists) {
                // 如果 key 存在，删除它以避免 WRONGTYPE 异常
                redisTemplate.delete(countCommentTotalKey);
            }

            ZSetOperations<String, Object> zSetOperations = redisTemplate.opsForZSet();
            for (CommentDO commentDO : commentDOS) {
                Double commentHeat = commentDO.getHeat();
                Long commentId = commentDO.getId();
                zSetOperations.add(countCommentTotalKey, commentId, commentHeat);
            }
            // 设置随机过期时间 单位：秒  5小时以内
            int expireSeconds = RandomUtil.randomInt(5 * 60 * 60);
            redisTemplate.expire(countCommentTotalKey, expireSeconds, TimeUnit.SECONDS);
            return null;
        });
    }
}
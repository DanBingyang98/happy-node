package com.danby.happynode.auth;

import com.danby.happynode.auth.domain.dataobject.UserDO;
import com.danby.happynode.auth.domain.mapper.UserDOMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationContextFactory;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.time.ZoneId;

@SpringBootTest
@Slf4j
class AuthApplicationTests {

    @Autowired
    UserDOMapper userDOMapper;

    @Test
    void contextLoads() {
    }

    @Test
    void testInsert() {
        userDOMapper.insert(UserDO.builder()
                .id(2L)
                .username("test")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build());
    }

    @Test
    void testSelect() {

        UserDO userDO = userDOMapper.selectByPrimaryKey(2L);
        log.info("userDO: {}", userDO);
    }

    @Test
    void testUpdate() {
        userDOMapper.updateByPrimaryKey(UserDO.builder()
                .id(1L)
                .username("NBA MVP ABC")
                .createTime(LocalDateTime.now(ZoneId.of("Asia/Shanghai")))
                .updateTime(LocalDateTime.now(ZoneId.of("Asia/Shanghai")))
                .build());
    }

    @Test
    void testDelete() {
        userDOMapper.deleteByPrimaryKey(1L);
    }

}

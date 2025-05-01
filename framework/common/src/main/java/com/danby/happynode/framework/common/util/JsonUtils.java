package com.danby.happynode.framework.common.util;

import com.danby.happynode.framework.common.constant.DateConstants;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.SneakyThrows;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class JsonUtils {
    // 定义一个静态的 ObjectMapper 对象
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // 静态代码块，用于初始化 ObjectMapper 对象
    static {
        // 设置 ObjectMapper 对象在反序列化时，如果遇到未知属性，不抛出异常
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // 设置 ObjectMapper 对象在序列化时，如果遇到空对象，不抛出异常
        OBJECT_MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        // 解决 LocalDateTime 的序列化问题
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(LocalDateTime.class,new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(DateConstants.Y_M_D_H_M_S_FORMAT)));
        javaTimeModule.addDeserializer(LocalDateTime.class,new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(DateConstants.Y_M_D_H_M_S_FORMAT)));
        OBJECT_MAPPER.registerModules(javaTimeModule);
    }

    @SneakyThrows
    // 将对象转换为JSON字符串
    public static String toJsonString(Object obj) {
        // 使用OBJECT_MAPPER将对象转换为JSON字符串
        return OBJECT_MAPPER.writeValueAsString(obj);
    }
}

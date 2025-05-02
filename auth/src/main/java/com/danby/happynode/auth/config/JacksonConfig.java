package com.danby.happynode.auth.config;

import com.danby.happynode.framework.common.constant.DateConstants;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.YearMonthDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.YearMonthSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

import static com.danby.happynode.framework.common.constant.DateConstants.*;


public class JacksonConfig {
    @Bean
    public ObjectMapper objectMapper() {
        // 初始化一个 ObjectMapper 对象，用于自定义 Jackson 的行为
        ObjectMapper objectMapper = new ObjectMapper();

        // 忽略未知属性
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        // 设置凡是为 null 的字段，返参中均不返回，请根据项目组约定是否开启
        // objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        // 设置时区
        objectMapper.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));

        // JavaTimeModule 用于指定序列化和反序列化规则
        JavaTimeModule javaTimeModule = new JavaTimeModule();

        // 支持 LocalDateTime、LocalDate、LocalTime
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DATE_FORMAT_Y_M_D_H_M_S_S));
        javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DATE_FORMAT_Y_M_D_H_M_S_S));
        javaTimeModule.addSerializer(LocalDate.class, new LocalDateSerializer(DATE_FORMAT_Y_M_D));
        javaTimeModule.addDeserializer(LocalDate.class, new LocalDateDeserializer(DATE_FORMAT_Y_M_D));
        javaTimeModule.addSerializer(LocalTime.class, new LocalTimeSerializer(DATE_FORMAT_H_M_S));
        javaTimeModule.addDeserializer(LocalTime.class, new LocalTimeDeserializer(DATE_FORMAT_H_M_S));
        // 支持 YearMonth
        javaTimeModule.addSerializer(YearMonth.class, new YearMonthSerializer(DATE_FORMAT_Y_M));
        javaTimeModule.addDeserializer(YearMonth.class, new YearMonthDeserializer(DATE_FORMAT_Y_M));

        objectMapper.registerModule(javaTimeModule);

        return objectMapper;
    }
}

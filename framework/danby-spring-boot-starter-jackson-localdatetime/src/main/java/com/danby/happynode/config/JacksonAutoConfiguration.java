package com.danby.happynode.config;

import com.danby.happynode.framework.common.util.JsonUtils;
import com.fasterxml.jackson.annotation.JsonInclude;
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
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.TimeZone;

import static com.danby.happynode.constant.DateConstants.*;


@AutoConfiguration
public class JacksonAutoConfiguration {
    @Bean
    @Primary
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        // 初始化一个 ObjectMapper 对象，用于自定义 Jackson 的行为
        // 使用 Builder 简化配置
        ObjectMapper objectMapper = builder.build();

        // 忽略未知属性
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        // 设置凡是为 null 的字段，返参中均不返回，请根据项目组约定是否开启
         objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        // 设置时区
        objectMapper.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));

        // JavaTimeModule 用于指定序列化和反序列化规则
        JavaTimeModule javaTimeModule = new JavaTimeModule();

        // 支持 LocalDateTime
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DATE_FORMAT_Y_M_D_H_M_S));
        javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DATE_FORMAT_Y_M_D_H_M_S));
        //支持 LocalDate
        javaTimeModule.addSerializer(LocalDate.class, new LocalDateSerializer(DATE_FORMAT_Y_M_D));
        javaTimeModule.addDeserializer(LocalDate.class, new LocalDateDeserializer(DATE_FORMAT_Y_M_D));
        // 支持 LocalTime
        javaTimeModule.addSerializer(LocalTime.class, new LocalTimeSerializer(DATE_FORMAT_H_M_S));
        javaTimeModule.addDeserializer(LocalTime.class, new LocalTimeDeserializer(DATE_FORMAT_H_M_S));
        // 支持 YearMonth
        javaTimeModule.addSerializer(YearMonth.class, new YearMonthSerializer(DATE_FORMAT_Y_M));
        javaTimeModule.addDeserializer(YearMonth.class, new YearMonthDeserializer(DATE_FORMAT_Y_M));

        objectMapper.registerModule(javaTimeModule);
        JsonUtils.init(objectMapper);
        return objectMapper;
    }
}

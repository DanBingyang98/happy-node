package com.danby.happynode.auth.alarm;

import com.danby.happynode.auth.alarm.impl.MailAlarmHelper;
import com.danby.happynode.auth.alarm.impl.SmsAlarmHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

@Configuration
public class AlarmConfig {

    @Bean
    @RefreshScope
    public AlarmHelper alarmHelper(@Value("${alarm.type}") String alarmType) {
        return switch (alarmType) {
            case "sms" -> new SmsAlarmHelper();
            case "mail" -> new MailAlarmHelper();
            default -> throw new IllegalArgumentException("Alarm type not supported");
        };
    }
}

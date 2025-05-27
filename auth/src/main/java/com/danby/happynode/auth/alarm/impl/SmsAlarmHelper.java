package com.danby.happynode.auth.alarm.impl;

import com.danby.happynode.auth.alarm.AlarmHelper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SmsAlarmHelper implements AlarmHelper {
    @Override
    public boolean send(String message) {
        log.info("==> send alarm by Sms, message: {}", message);
        return true;
    }
}

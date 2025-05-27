package com.danby.happynode.auth.alarm.impl;

import com.danby.happynode.auth.alarm.AlarmHelper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MailAlarmHelper implements AlarmHelper {

    @Override
    public boolean send(String message) {
        // TODO Auto-generated method stub
        log.info("==> send alarm by Email, message: {}", message);
        return true;
    }

}

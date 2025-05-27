package com.danby.happynode.auth.alarm;

public interface AlarmHelper {
    /**
     * 发送告警信息
     *
     * @param message
     * @return
     */
    boolean send(String message);
}

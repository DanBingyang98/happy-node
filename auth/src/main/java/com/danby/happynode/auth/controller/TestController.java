package com.danby.happynode.auth.controller;

import com.danby.happynode.auth.alarm.AlarmHelper;
import com.danby.happynode.framework.common.response.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class TestController {
    @Autowired
    AlarmHelper alarmHelper;

    @GetMapping("/alarm")
    public Response<String> sendAlarm() {
        alarmHelper.send("Alarm !");
        return Response.success();
    }
}

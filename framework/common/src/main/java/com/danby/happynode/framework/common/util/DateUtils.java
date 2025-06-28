package com.danby.happynode.framework.common.util;

import com.danby.happynode.framework.common.constant.DateConstants;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

public class DateUtils {

    /**
     * LocalDateTime 转时间戳
     *
     * @param localDateTime
     * @return
     */
    public static long localDateTime2Timestamp(LocalDateTime localDateTime) {
        return localDateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    public static String localDateTime2String(LocalDateTime localDateTime) {
        return localDateTime.format(DateConstants.DATE_FORMAT_Y_M_D_H_M_S);

    }

    /**
     * LocalDateTime 转友好的相对时间字符串
     * @param dateTime
     * @return
     */
    public static String formatRelativeTime(LocalDateTime dateTime) {
        // 当前时间
        LocalDateTime now = LocalDateTime.now();
        // 计算与当前时间的差距
        long daysDiff = ChronoUnit.DAYS.between(dateTime, now);
        long hoursDiff = ChronoUnit.HOURS.between(dateTime, now);
        long minutesDiff = ChronoUnit.MINUTES.between(dateTime, now);

        if (daysDiff < 1) {
            if (hoursDiff < 1) {
                return minutesDiff < 1 ? "刚刚" : minutesDiff + "分钟前";
            } else {
                return hoursDiff + "小时前";
            }
        } else if (daysDiff < 2) {
            return "昨天";
        } else if (daysDiff < 7) {
            return daysDiff + "天前";
        } else if (dateTime.getYear() == now.getYear()) {  // 如果是今年
            return dateTime.format(DateConstants.DATE_FORMAT_M_D);
        } else {  // 如果是去年或更早
            return dateTime.format(DateConstants.DATE_FORMAT_Y_M_D);
        }
    }

    public static void main(String[] args) {
        // 测试示例
        LocalDateTime dateTime1 = LocalDateTime.now().minusMinutes(10); // 10分钟前
        LocalDateTime dateTime2 = LocalDateTime.now().minusHours(3); // 3小时前
        LocalDateTime dateTime3 = LocalDateTime.now().minusDays(1).minusHours(5); // 昨天 20:12
        LocalDateTime dateTime4 = LocalDateTime.now().minusDays(2); // 2天前
        LocalDateTime dateTime5 = LocalDateTime.now().minusDays(10); // 11-06
        LocalDateTime dateTime6 = LocalDateTime.of(2023, 12, 1, 12, 30, 0); // 2023-12-01

        System.out.println(formatRelativeTime(dateTime1)); // 输出 "10分钟前"
        System.out.println(formatRelativeTime(dateTime2)); // 输出 "3小时前"
        System.out.println(formatRelativeTime(dateTime3)); // 输出 "昨天 20:12"
        System.out.println(formatRelativeTime(dateTime4)); // 输出 "2天前"
        System.out.println(formatRelativeTime(dateTime5)); // 输出 "11-06"
        System.out.println(formatRelativeTime(dateTime6)); // 输出 "2023-12-01"
    }
}

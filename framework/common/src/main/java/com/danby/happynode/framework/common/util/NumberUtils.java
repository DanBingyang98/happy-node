package com.danby.happynode.framework.common.util;

import java.math.RoundingMode;
import java.text.DecimalFormat;

public class NumberUtils {
    /**
     * 数字转换字符串
     *
     * @param number
     * @return
     */
    public static String formatNumberString(long number) {
        if (number < 10000) {
            return String.valueOf(number);  // 小于 1 万显示原始数字
        } else if (number >= 10000 && number < 100000000) {
            // 小于 1 亿，显示万单位
            double result = number / 10000.0;
            DecimalFormat df = new DecimalFormat("#.#"); // 保留 1 位小数
            df.setRoundingMode(RoundingMode.DOWN); // 禁用四舍五入
            String formatted = df.format(result);
            return formatted + "万";
        } else {  // 超过 1 亿
            double result = number / 100000000.0;
            DecimalFormat df = new DecimalFormat("#.#");
            df.setRoundingMode(RoundingMode.DOWN);
            String formatted = df.format(result);
            return formatted + "亿";
        }
    }

    public static void main(String[] args) {
        // 测试
        System.out.println(formatNumberString(1000));         // 1000
        System.out.println(formatNumberString(11130));        // 1.1万
        System.out.println(formatNumberString(26719300));     // 2671.9万
        System.out.println(formatNumberString(10000000));    // 1000万
        System.out.println(formatNumberString(999999));       // 99.9万
        System.out.println(formatNumberString(150000000));    // 1.5亿
        System.out.println(formatNumberString(99999));        // 9.9万
    }

}

package com.zhoushuo.framework.commono.eumns;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ProcessStatusEnum {

    /**
     * 处理成功
     */
    SUCCESS("SUCCESS", "处理成功"),

    /**
     * 处理失败
     */
    FAILED("FAILED", "处理失败"),

    /**
     * 等待处理
     */
    WAITING("WAITING", "等待处理"),

    /**
     * 处理中
     */
    PROCESSING("PROCESSING", "处理中");

    /**
     * 状态值
     */
    private final String value;

    /**
     * 状态描述
     */
    private final String desc;

    /**
     * 根据状态值获取枚举实例
     * @param value 状态值
     * @return 枚举实例
     */
    public static ProcessStatusEnum getByValue(String value) {
        if (value == null) {
            return null;
        }
        for (ProcessStatusEnum status : values()) {
            if (status.getValue().equals(value)) {
                return status;
            }
        }
        return null;
    }
}
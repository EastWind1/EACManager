package pers.eastwind.billmanager.servicebill.model;

import lombok.Getter;

@Getter
public enum ServiceBillState {
    CREATED("新建"),
    PROCESSING("进行中"),
    PROCESSED("处理完成"),
    FINISHED("完成");

    private final String label;

    ServiceBillState(String label) {
        this.label = label;
    }

}

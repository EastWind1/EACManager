package com.eastwind.ElevatorACAfterSaleManager.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 公司实体
 */
@Entity
@Data
@EqualsAndHashCode(callSuper=true)
public class Company extends AuditEntity {
    @Id
    @GeneratedValue
    private int id;

    /**
     * 名称
     */
    private String name;

    /**
     * 联系人姓名
     */
    private String contactName;

    /**
     * 联系人电话
     */
    private String contactPhone;

    /**
     * 地址
     */
    private String address;

}

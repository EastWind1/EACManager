package pers.eastwind.billmanager.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Data;

/**
 * 公司实体
 */
@Entity
@Data
public class Company {
    @Id
    @GeneratedValue
    private Integer id;

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

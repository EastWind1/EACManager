package pers.eastwind.billmanager.company.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.EqualsAndHashCode;
import pers.eastwind.billmanager.common.model.AuditEntity;

/**
 * 公司实体
 */

@Entity
@Data
@EqualsAndHashCode(callSuper = true)
public class Company extends AuditEntity {
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
     * 邮箱
     */
    private String email;
    /**
     * 地址
     */
    private String address;
    /**
     * 是否禁用
     */
    private Boolean isDisabled = false;

}

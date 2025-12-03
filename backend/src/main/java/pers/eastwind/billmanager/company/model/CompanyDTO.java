package pers.eastwind.billmanager.company.model;

import lombok.Data;

@Data
public class CompanyDTO {
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

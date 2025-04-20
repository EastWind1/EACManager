package com.eastwind.EACAfterSaleMgr.dto;

import lombok.Data;

@Data
public class CompanyDTO {
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

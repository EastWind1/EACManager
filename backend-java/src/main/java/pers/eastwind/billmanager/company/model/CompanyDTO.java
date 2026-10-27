package pers.eastwind.billmanager.company.model;

import lombok.Data;

@Data
public class CompanyDTO {
    private Integer id;

    private String name;

    private String contactName;

    private String contactPhone;

    private String email;

    private String address;
}

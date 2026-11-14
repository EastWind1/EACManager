package pers.eastwind.billmanager.company.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CompanyDTO {
    private Integer id;

    @NotBlank(message = "公司名称不能为空")
    @Size(max = 100, message = "公司名称不能超过 100 个字符")
    private String name;

    @Size(max = 64, message = "联系人不能超过 64 个字符")
    private String contactName;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "联系电话格式不正确")
    private String contactPhone;

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱不能超过 128 个字符")
    private String email;

    @Size(max = 255, message = "地址不能超过 255 个字符")
    private String address;
}

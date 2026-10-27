package pers.eastwind.billmanager.attach.model;

import lombok.Data;

@Data
public class AttachDTO {
    private Integer id;
    private String name;
    private AttachType type = AttachType.OTHER;
}

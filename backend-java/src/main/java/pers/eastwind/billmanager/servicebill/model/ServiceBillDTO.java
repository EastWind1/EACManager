package pers.eastwind.billmanager.servicebill.model;

import lombok.Data;
import pers.eastwind.billmanager.attach.model.AttachDTO;
import pers.eastwind.billmanager.company.model.CompanyDTO;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
public class ServiceBillDTO {
    private Integer id;
    private String number;
    private ServiceBillType type = ServiceBillType.INSTALL;
    private ServiceBillState state = ServiceBillState.CREATED;
    private CompanyDTO productCompany;
    private String projectName;
    private String projectAddress;
    private String projectContact;
    private String projectContactPhone;
    private String onSiteContact;
    private String onSitePhone;
    private String elevatorInfo;
    private List<ServiceBillDetailDTO> details = new ArrayList<>();
    private List<AttachDTO> attachments = new ArrayList<>();
    private BigDecimal totalAmount;
    private Instant orderDate = Instant.now();
    private Instant processedDate;
    private Instant finishedDate;
    private String remark;
}

package pers.eastwind.billmanager.reimburse.model;

import lombok.Data;
import pers.eastwind.billmanager.attach.model.AttachDTO;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
public class ReimburseDTO {
    private Integer id;
    private String number;
    private ReimburseState state = ReimburseState.CREATED;
    private String summary;
    private BigDecimal totalAmount;
    private Instant reimburseDate;
    private String remark;

    private List<ReimburseDetailDTO> details = new ArrayList<>();

    private List<AttachDTO> attachments = new ArrayList<>();
}

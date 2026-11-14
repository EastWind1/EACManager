package pers.eastwind.billmanager.reimburse.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    @NotBlank(message = "摘要不能为空")
    @Size(max = 255, message = "摘要不能超过 255 个字符")
    private String summary;
    private BigDecimal totalAmount;
    private Instant reimburseDate;
    private String remark;

    @Valid
    private List<ReimburseDetailDTO> details = new ArrayList<>();

    private List<AttachDTO> attachments = new ArrayList<>();
}

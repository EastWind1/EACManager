package pers.eastwind.billmanager.servicebill.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import pers.eastwind.billmanager.common.model.QueryParam;

import java.time.Instant;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ServiceBillQueryParam extends QueryParam {
    private String number;
    private List<ServiceBillState> states;
    private String projectName;
    private Instant orderStartDate;
    private Instant orderEndDate;
    private Instant processedStartDate;
    private Instant processedEndDate;
}

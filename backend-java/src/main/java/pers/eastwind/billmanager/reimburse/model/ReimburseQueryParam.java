package pers.eastwind.billmanager.reimburse.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import pers.eastwind.billmanager.common.model.QueryParam;

import java.time.Instant;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ReimburseQueryParam extends QueryParam {
    private String number;
    private String summary;
    private List<ReimburseState> states;
    private Instant reimburseStartDate;
    private Instant reimburseEndDate;
}

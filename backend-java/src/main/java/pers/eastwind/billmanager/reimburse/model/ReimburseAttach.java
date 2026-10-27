package pers.eastwind.billmanager.reimburse.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "reimburse_attach_relation",
        indexes = @Index(columnList = "reimburseId"))
public class ReimburseAttach {
    @Id
    @Column(insertable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private Integer reimburseId;
    private Integer attachId;
}

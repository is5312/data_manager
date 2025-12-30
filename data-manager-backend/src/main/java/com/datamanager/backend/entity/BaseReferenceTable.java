package com.datamanager.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * JPA Entity for base_reference_table
 * Stores metadata about logical tables
 */
@Entity
@Table(name = "base_reference_table")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaseReferenceTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tbl_label", nullable = false)
    private String tblLabel;

    @Column(name = "tbl_link", nullable = false, unique = true)
    private String tblLink;

    @Column(name = "description")
    private String description;

    @Column(name = "version_no")
    private Integer versionNo = 1;

    @Column(name = "add_ts")
    private LocalDateTime addTs;

    @Column(name = "add_usr")
    private String addUsr;

    @Column(name = "upd_ts")
    private LocalDateTime updTs;

    @Column(name = "upd_usr")
    private String updUsr;

    @OneToMany(mappedBy = "referenceTable", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BaseColumnMap> columns;

    @PrePersist
    protected void onCreate() {
        addTs = LocalDateTime.now();
        if (addUsr == null) {
            addUsr = "system";
        }
        if (versionNo == null) {
            versionNo = 1;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updTs = LocalDateTime.now();
        if (versionNo != null) {
            versionNo++;
        }
    }
}

package com.datamanager.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * JPA Entity for base_column_map
 * Stores metadata about logical columns in tables
 */
@Entity
@Table(name = "base_column_map")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaseColumnMap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tbl_id", nullable = false)
    private BaseReferenceTable referenceTable;

    @Column(name = "tbl_link", nullable = false)
    private String tblLink;

    @Column(name = "col_label", nullable = false)
    private String colLabel;

    @Column(name = "col_link", nullable = false)
    private String colLink;

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

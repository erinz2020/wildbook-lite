package com.wildme.wildbook_lite.taxonomy;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

/**
 * Species reference data — the master record for "what animal is this".
 *
 * Why this isn't just a string column on Encounter anymore:
 *  - Multiple Encounters of the same species should reference one row,
 *    not duplicate the name N times (typos creep in: "Humpback whale"
 *    vs "humpback whale" vs "Megaptera novaeangliae").
 *  - Adding fields here (common names, ITIS Taxonomic Serial Number,
 *    parent genus) is a single-row edit instead of a mass UPDATE.
 *  - External integrations want a stable id, not the human-readable name.
 *
 * We keep `Encounter.species` as a denormalized cache for fast filtering
 * — it's the cheapest way to keep the existing /api/encounters?species=
 * endpoint snappy without joining taxonomy on every list request.
 * Service code keeps `species` and `taxonomy.scientificName` in sync on
 * write.
 */
@Entity
@Table(
    name = "taxonomy",
    uniqueConstraints = @UniqueConstraint(name = "uk_taxonomy_scientific",
                                          columnNames = "scientific_name"),
    indexes = {
        @Index(name = "ix_taxonomy_genus", columnList = "genus"),
        @Index(name = "ix_taxonomy_specific", columnList = "specific_epithet")
    }
)
public class Taxonomy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Binomial nomenclature, e.g. "Megaptera novaeangliae".
     * Unique — this is the natural key.
     */
    @Column(name = "scientific_name", nullable = false, length = 128)
    private String scientificName;

    /** Genus part of the binomial, denormalized for fast prefix search. */
    @Column(length = 64)
    private String genus;

    /** Specific epithet (the second word of the binomial). */
    @Column(name = "specific_epithet", length = 64)
    private String specificEpithet;

    /**
     * Comma-separated list of common names ("Humpback whale, Yubarta").
     * Kept as text not a separate table — common names are write-rarely
     * data and a search index is a better tool than a SQL join when
     * we want fuzzy "find by any common name" queries.
     */
    @Column(name = "common_names", length = 512)
    private String commonNames;

    /**
     * Integrated Taxonomic Information System Serial Number — the
     * canonical reference id for any species. Used to cross-link with
     * external biodiversity databases (GBIF, IUCN Red List, etc.).
     */
    @Column(name = "itis_tsn")
    private Long itisTsn;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Version
    private Long version;

    public Taxonomy() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getScientificName() { return scientificName; }
    public void setScientificName(String scientificName) { this.scientificName = scientificName; }

    public String getGenus() { return genus; }
    public void setGenus(String genus) { this.genus = genus; }

    public String getSpecificEpithet() { return specificEpithet; }
    public void setSpecificEpithet(String specificEpithet) { this.specificEpithet = specificEpithet; }

    public String getCommonNames() { return commonNames; }
    public void setCommonNames(String commonNames) { this.commonNames = commonNames; }

    public Long getItisTsn() { return itisTsn; }
    public void setItisTsn(Long itisTsn) { this.itisTsn = itisTsn; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}

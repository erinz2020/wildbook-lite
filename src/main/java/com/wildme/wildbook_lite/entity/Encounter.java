package com.wildme.wildbook_lite.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.wildme.wildbook_lite.encounter.EncounterStatus;
import com.wildme.wildbook_lite.encounter.LivingStatus;
import com.wildme.wildbook_lite.occurrence.Occurrence;
import com.wildme.wildbook_lite.taxonomy.Taxonomy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.FetchType;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import com.fasterxml.jackson.annotation.JsonIgnore;


@Entity
@Table(name = "encounter", indexes = {
    @Index(name = "ix_encounter_project", columnList = "project_id"),
    @Index(name = "ix_encounter_species", columnList = "species"),
    @Index(name = "ix_encounter_status", columnList = "status"),
    @Index(name = "ix_encounter_occurrence", columnList = "occurrence_id"),
    @Index(name = "ix_encounter_taxonomy", columnList = "taxonomy_id"),
    @Index(name = "ix_encounter_location_id", columnList = "location_id")
})
public class Encounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id")
    private Long projectId;

    private String location;

    /**
     * Denormalized species name. Kept in sync with taxonomy.scientificName
     * by EncounterService when a taxonomy is assigned. Read it for fast
     * filtering and listing; for authoritative species questions use
     * the taxonomy relation.
     */
    private String species;

    /**
     * Optional reference to the species catalogue. Nullable because
     * old rows may have only the denormalized `species` string from
     * before the taxonomy table existed.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "taxonomy_id")
    private Taxonomy taxonomy;

    /**
     * Hierarchical location path, e.g. "USA/CA/Monterey Bay". Stored as
     * one column so we can do prefix queries (`location_id LIKE 'USA/%'`)
     * without joining a separate place table — pragmatic shortcut that
     * mirrors real Wildbook's LOCATIONID convention.
     */
    @Column(name = "location_id", length = 255)
    private String locationId;

    @Column(name = "decimal_latitude")
    private Double decimalLatitude;

    @Column(name = "decimal_longitude")
    private Double decimalLongitude;

    /** "juvenile", "sub-adult", "adult", ... Free string by convention. */
    @Column(name = "life_stage", length = 32)
    private String lifeStage;

    /** Free-text behavior at the time of encounter. */
    @Column(columnDefinition = "text")
    private String behavior;

    /** Alive at time of observation, dead-stranding, or unknown. */
    @Enumerated(EnumType.STRING)
    @Column(name = "living_status", length = 16)
    private LivingStatus livingStatus = LivingStatus.UNKNOWN;

    /**
     * Free-form per-encounter properties. Stored as a JSON string in a
     * TEXT column (kept portable — no Postgres-specific jsonb mapping
     * needed at the JPA level). For querying, callers parse it
     * application-side. Real Wildbook treats this as the escape hatch
     * for site-specific fields that don't belong in the schema.
     */
    @Column(name = "dynamic_properties", columnDefinition = "text")
    private String dynamicProperties;

    private LocalDateTime encounterDate;
    private String notes;

    /**
     * Workflow state. New encounters land in DRAFT. Transitions are
     * managed by EncounterService.transition() through EncounterStatus's
     * state-machine table.
     *
     * @Enumerated(STRING) so the column stores the name ("DRAFT"), not
     * the ordinal. ORDINAL is a footgun — reordering the enum silently
     * remaps every existing row.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private EncounterStatus status = EncounterStatus.DRAFT;

    /**
     * Optional reviewer / responsible user. We deliberately store just
     * the id (not a JPA relation): User and Encounter live in different
     * aggregates, so navigating with @ManyToOne would tempt callers to
     * fetch users transitively and ruin the lazy-loading contract.
     */
    @Column(name = "assigned_to_user_id")
    private Long assignedToUserId;

    /**
     * Who *submitted* this encounter. Distinct from:
     *   - Observer (the field researcher who saw it; may not have an account)
     *   - assignedToUserId (the current reviewer)
     *   - audit_log entries (technical record, may be ADMIN acting on behalf)
     *
     * Set once at creation/report time. Used for "my reports" queries.
     */
    @Column(name = "submitter_user_id")
    private Long submitterUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "individual_id")
    private Individual individual;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "observer_id")
    private Observer observer;

    /**
     * Optional parent group event. Many encounters can share one
     * Occurrence (single survey, multiple animals). FK lives on this
     * side; Occurrence exposes the reverse as @OneToMany(mappedBy="occurrence").
     *
     * Nullable on purpose — solo encounters (e.g., a single opportunistic
     * sighting) don't need to be tied to a survey event.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "occurrence_id")
    private Occurrence occurrence;

    @JsonIgnore
    @OneToMany(mappedBy = "encounter")
    private List<MediaAsset> mediaAssets = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "encounter")
    private List<Sighting> sightings = new ArrayList<>();

    public Encounter() {}

    @Version
    private Long version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getSpecies() { return species; }
    public void setSpecies(String species) { this.species = species; }

    public LocalDateTime getEncounterDate() { return encounterDate; }
    public void setEncounterDate(LocalDateTime encounterDate) { this.encounterDate = encounterDate; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public EncounterStatus getStatus() { return status; }
    public void setStatus(EncounterStatus status) { this.status = status; }

    public Long getAssignedToUserId() { return assignedToUserId; }
    public void setAssignedToUserId(Long assignedToUserId) { this.assignedToUserId = assignedToUserId; }

    public Long getSubmitterUserId() { return submitterUserId; }
    public void setSubmitterUserId(Long submitterUserId) { this.submitterUserId = submitterUserId; }

    public Individual getIndividual() { return individual; }
    public void setIndividual(Individual individual) { this.individual = individual; }

    public Observer getObserver() { return observer; }
    public void setObserver(Observer observer) { this.observer = observer; }

    public Occurrence getOccurrence() { return occurrence; }
    public void setOccurrence(Occurrence occurrence) { this.occurrence = occurrence; }

    public Taxonomy getTaxonomy() { return taxonomy; }
    public void setTaxonomy(Taxonomy taxonomy) { this.taxonomy = taxonomy; }

    public String getLocationId() { return locationId; }
    public void setLocationId(String locationId) { this.locationId = locationId; }

    public Double getDecimalLatitude() { return decimalLatitude; }
    public void setDecimalLatitude(Double decimalLatitude) { this.decimalLatitude = decimalLatitude; }

    public Double getDecimalLongitude() { return decimalLongitude; }
    public void setDecimalLongitude(Double decimalLongitude) { this.decimalLongitude = decimalLongitude; }

    public String getLifeStage() { return lifeStage; }
    public void setLifeStage(String lifeStage) { this.lifeStage = lifeStage; }

    public String getBehavior() { return behavior; }
    public void setBehavior(String behavior) { this.behavior = behavior; }

    public LivingStatus getLivingStatus() { return livingStatus; }
    public void setLivingStatus(LivingStatus livingStatus) { this.livingStatus = livingStatus; }

    public String getDynamicProperties() { return dynamicProperties; }
    public void setDynamicProperties(String dynamicProperties) { this.dynamicProperties = dynamicProperties; }

    public List<Sighting> getSightings() { return sightings; }
    public void setSightings(List<Sighting> sightings) { this.sightings = sightings; }
}

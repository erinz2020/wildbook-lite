package com.wildme.wildbook_lite.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.wildme.wildbook_lite.encounter.EncounterStatus;
import com.wildme.wildbook_lite.occurrence.Occurrence;

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
    @Index(name = "ix_encounter_occurrence", columnList = "occurrence_id")
})
public class Encounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id")
    private Long projectId;

    private String location;
    private String species;
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

    public List<Sighting> getSightings() { return sightings; }
    public void setSightings(List<Sighting> sightings) { this.sightings = sightings; }
}

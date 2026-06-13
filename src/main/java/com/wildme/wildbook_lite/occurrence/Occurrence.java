package com.wildme.wildbook_lite.occurrence;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.wildme.wildbook_lite.entity.Encounter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A single survey event in which one or more animals were spotted. One
 * Occurrence → N Encounters (each Encounter is "one animal in that
 * group at that time"). Event-level data lives here: weather, platform,
 * group-size estimate, transect ID. Per-animal data lives on Encounter.
 *
 * Why split this off Encounter:
 *  - Group composition (4 adults + 2 calves) is a property of the
 *    sighting moment, not of any one whale.
 *  - The same group might be re-spotted later; modeling Occurrence as
 *    a first-class entity lets us aggregate "how many groups of
 *    species X did we see this season".
 *  - Survey effort metadata (transect, platform) ties to the
 *    Occurrence, not to individual animals — needed for proper CPUE
 *    (catch per unit effort) analyses.
 *
 * Ownership:
 *  - Occurrence is the aggregate root for the group event.
 *  - Encounter is the owning side of the FK (encounter.occurrence_id).
 *    We expose the reverse list via @OneToMany(mappedBy="occurrence"),
 *    JsonIgnored to keep serialization cheap.
 */
@Entity
@Table(name = "occurrence", indexes = {
    @Index(name = "ix_occurrence_project_date", columnList = "project_id,date_time"),
    @Index(name = "ix_occurrence_submitter",    columnList = "submitter_user_id")
})
public class Occurrence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id")
    private Long projectId;

    /** When the survey event happened. Naive local; we don't track TZ here. */
    @Column(name = "date_time")
    private LocalDateTime dateTime;

    /** Free-text location (named site, "Maui north shore"). */
    private String location;

    /** Optional GPS. Stored as decimal degrees, WGS84. */
    @Column(name = "decimal_latitude")
    private Double decimalLatitude;

    @Column(name = "decimal_longitude")
    private Double decimalLongitude;

    /** Free-text weather: "clear, sea state 2, no wind". Could be normalized later. */
    private String weather;

    /** Survey platform — see {@link Platform}. */
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Platform platform;

    /** Optional transect ID (named survey track within the project). */
    private String transect;

    /**
     * Group size estimates. The observer often can't get an exact count;
     * we store a min/max range. If both are equal it's a precise count.
     */
    @Column(name = "group_size_min")
    private Integer groupSizeMin;

    @Column(name = "group_size_max")
    private Integer groupSizeMax;

    /** Composition estimates (best-effort, often null). */
    @Column(name = "num_adults")
    private Integer numAdults;

    @Column(name = "num_juveniles")
    private Integer numJuveniles;

    @Column(name = "num_calves")
    private Integer numCalves;

    @Column(columnDefinition = "text")
    private String comments;

    /**
     * Who logged this Occurrence. Same rationale as Encounter.submitterUserId:
     * store the id, NOT a @ManyToOne, because User lives in a different
     * aggregate and we don't want JPA navigation from here.
     */
    @Column(name = "submitter_user_id")
    private Long submitterUserId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Reverse view of Encounter.occurrence. Read-only on this side —
     * Encounter owns the FK. JsonIgnored to avoid recursive serialization
     * (encounter → occurrence → encounters → ...).
     */
    @JsonIgnore
    @OneToMany(mappedBy = "occurrence")
    private List<Encounter> encounters = new ArrayList<>();

    @Version
    private Long version;

    public Occurrence() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public LocalDateTime getDateTime() { return dateTime; }
    public void setDateTime(LocalDateTime dateTime) { this.dateTime = dateTime; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public Double getDecimalLatitude() { return decimalLatitude; }
    public void setDecimalLatitude(Double decimalLatitude) { this.decimalLatitude = decimalLatitude; }

    public Double getDecimalLongitude() { return decimalLongitude; }
    public void setDecimalLongitude(Double decimalLongitude) { this.decimalLongitude = decimalLongitude; }

    public String getWeather() { return weather; }
    public void setWeather(String weather) { this.weather = weather; }

    public Platform getPlatform() { return platform; }
    public void setPlatform(Platform platform) { this.platform = platform; }

    public String getTransect() { return transect; }
    public void setTransect(String transect) { this.transect = transect; }

    public Integer getGroupSizeMin() { return groupSizeMin; }
    public void setGroupSizeMin(Integer groupSizeMin) { this.groupSizeMin = groupSizeMin; }

    public Integer getGroupSizeMax() { return groupSizeMax; }
    public void setGroupSizeMax(Integer groupSizeMax) { this.groupSizeMax = groupSizeMax; }

    public Integer getNumAdults() { return numAdults; }
    public void setNumAdults(Integer numAdults) { this.numAdults = numAdults; }

    public Integer getNumJuveniles() { return numJuveniles; }
    public void setNumJuveniles(Integer numJuveniles) { this.numJuveniles = numJuveniles; }

    public Integer getNumCalves() { return numCalves; }
    public void setNumCalves(Integer numCalves) { this.numCalves = numCalves; }

    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }

    public Long getSubmitterUserId() { return submitterUserId; }
    public void setSubmitterUserId(Long submitterUserId) { this.submitterUserId = submitterUserId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<Encounter> getEncounters() { return encounters; }
    public void setEncounters(List<Encounter> encounters) { this.encounters = encounters; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}

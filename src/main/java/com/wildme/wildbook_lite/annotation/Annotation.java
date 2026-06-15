package com.wildme.wildbook_lite.annotation;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.wildme.wildbook_lite.entity.Encounter;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A region of interest on one or more MediaAssets that captures ONE
 * animal (or fluke / fin / scar — whatever the matcher cares about).
 *
 * Why an Annotation isn't a column on MediaAsset:
 *  - One image can contain N animals → N Annotations on one MediaAsset.
 *  - One Annotation can theoretically reference multiple MediaAssets
 *    (cropped + original); this is where Feature comes in.
 *  - The bbox lives ON the Annotation, but the link to the MediaAsset
 *    goes THROUGH a Feature (the bridge), which is the gotcha-iest
 *    schema decision in real Wildbook.
 *
 * Two views:
 *   - Annotation -> Encounter: which encounter this animal belongs to.
 *   - Annotation -> Feature(s) -> MediaAsset(s): which images contain it.
 *
 * For wildbook-lite we keep it simpler than the real platform:
 *   - bbox params (x/y/w/h/theta) live directly on Annotation.
 *   - Features only carry the linkage + the BBOX vs TRIVIAL flag.
 *
 * That diverges a bit from real Wildbook (where bbox can live on
 * Feature too) but keeps the model legible while still demonstrating
 * the bridge-entity pattern.
 */
@Entity
@Table(name = "annotation", indexes = {
    @Index(name = "ix_annotation_encounter", columnList = "encounter_id"),
    @Index(name = "ix_annotation_viewpoint", columnList = "viewpoint"),
    @Index(name = "ix_annotation_species",   columnList = "species")
})
public class Annotation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Bounding box in image pixel space.
     *  x, y = top-left corner
     *  width, height = box size
     *  theta = rotation in radians (0 if not rotated)
     *
     * Null when the Annotation only carries TRIVIAL Features
     * (whole-image, no detector box yet).
     */
    @Column(name = "bbox_x")
    private Double x;

    @Column(name = "bbox_y")
    private Double y;

    @Column(name = "bbox_width")
    private Double width;

    @Column(name = "bbox_height")
    private Double height;

    @Column(name = "bbox_theta")
    private Double theta;

    /** Species at the annotation level — can be more specific than the encounter species. */
    @Column(length = 64)
    private String species;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Viewpoint viewpoint = Viewpoint.UNKNOWN;

    /** 0..1 quality score from the detector. Manual reviewers may overwrite. */
    private Double quality;

    /**
     * "This is the canonical photo of this individual" — used to pick a
     * thumbnail and to prefer it for downstream matching. At most one
     * exemplar per Encounter is the usual convention but we don't
     * enforce uniqueness at the DB level.
     */
    @Column(name = "is_exemplar", nullable = false)
    private boolean exemplar = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "encounter_id")
    private Encounter encounter;

    /**
     * Bridge to MediaAsset(s). One annotation usually has one Feature
     * (one image), but the bridge lets us evolve to multi-image
     * annotations (cropped derivative + original).
     *
     * CascadeType.ALL + orphanRemoval=true: deleting an Annotation
     * deletes its Features, and removing a Feature from this list
     * deletes it from the DB. The MediaAsset itself is NOT cascaded —
     * Feature is just the join row.
     */
    @JsonIgnore
    @OneToMany(mappedBy = "annotation",
               cascade = CascadeType.ALL,
               orphanRemoval = true,
               fetch = FetchType.LAZY)
    private List<Feature> features = new ArrayList<>();

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    public Annotation() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Double getX() { return x; }
    public void setX(Double x) { this.x = x; }

    public Double getY() { return y; }
    public void setY(Double y) { this.y = y; }

    public Double getWidth() { return width; }
    public void setWidth(Double width) { this.width = width; }

    public Double getHeight() { return height; }
    public void setHeight(Double height) { this.height = height; }

    public Double getTheta() { return theta; }
    public void setTheta(Double theta) { this.theta = theta; }

    public String getSpecies() { return species; }
    public void setSpecies(String species) { this.species = species; }

    public Viewpoint getViewpoint() { return viewpoint; }
    public void setViewpoint(Viewpoint viewpoint) { this.viewpoint = viewpoint; }

    public Double getQuality() { return quality; }
    public void setQuality(Double quality) { this.quality = quality; }

    public boolean isExemplar() { return exemplar; }
    public void setExemplar(boolean exemplar) { this.exemplar = exemplar; }

    public Encounter getEncounter() { return encounter; }
    public void setEncounter(Encounter encounter) { this.encounter = encounter; }

    public List<Feature> getFeatures() { return features; }
    public void setFeatures(List<Feature> features) { this.features = features; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}

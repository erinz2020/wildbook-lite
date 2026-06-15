package com.wildme.wildbook_lite.annotation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.wildme.wildbook_lite.entity.MediaAsset;

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
import jakarta.persistence.Table;

/**
 * Bridge entity between Annotation and MediaAsset.
 *
 * Why a bridge instead of a direct @ManyToMany or a column on
 * Annotation:
 *  - A direct FK from Annotation→MediaAsset would tightly couple
 *    annotation lifecycle to a single image. In practice, an
 *    annotation can have multiple "feature views" (original + cropped
 *    derivative); each view points at a different MediaAsset.
 *  - The bridge gives us a place to attach per-link metadata (type:
 *    BBOX vs TRIVIAL, params JSON if we go richer later) without
 *    polluting either parent table.
 *  - This is exactly the model real Wildbook uses: ANNOTATION_FEATURES
 *    and MEDIAASSET_FEATURES join tables that share a Feature row.
 *
 * For wildbook-lite we collapse to one Feature table with two FKs.
 * That's enough to demonstrate the pattern without the join-table
 * archaeology real Wildbook carries from its JDO heritage.
 */
@Entity
@Table(name = "feature", indexes = {
    @Index(name = "ix_feature_annotation",  columnList = "annotation_id"),
    @Index(name = "ix_feature_media_asset", columnList = "media_asset_id")
})
public class Feature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private FeatureType type = FeatureType.BBOX;

    /**
     * The Annotation this Feature attaches to. Eager on the Feature
     * side would create surprise N+1 from MediaAsset.features → annotation;
     * LAZY is correct here.
     */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "annotation_id", nullable = false)
    private Annotation annotation;

    /**
     * The image. NOT nullable — a Feature without a MediaAsset is
     * meaningless (you'd just edit the Annotation directly).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_asset_id", nullable = false)
    private MediaAsset mediaAsset;

    public Feature() {}

    public Feature(Annotation annotation, MediaAsset mediaAsset, FeatureType type) {
        this.annotation = annotation;
        this.mediaAsset = mediaAsset;
        this.type = type;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public FeatureType getType() { return type; }
    public void setType(FeatureType type) { this.type = type; }

    public Annotation getAnnotation() { return annotation; }
    public void setAnnotation(Annotation annotation) { this.annotation = annotation; }

    public MediaAsset getMediaAsset() { return mediaAsset; }
    public void setMediaAsset(MediaAsset mediaAsset) { this.mediaAsset = mediaAsset; }
}

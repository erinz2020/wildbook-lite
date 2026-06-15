package com.wildme.wildbook_lite.annotation;

/**
 * What kind of region on the image this Feature represents.
 *
 *  - BBOX:     an explicit bounding box. The Annotation's x/y/w/h/theta
 *              fields are meaningful.
 *  - TRIVIAL:  "the whole image is the annotation" — used when no
 *              detector has yet drawn a box, but we still need
 *              something to feed downstream tasks like ID matching.
 *              Real Wildbook calls this the "trivial annotation"
 *              pattern.
 */
public enum FeatureType {
    BBOX,
    TRIVIAL
}

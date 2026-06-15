package com.wildme.wildbook_lite.taxonomy;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxonomyRepository extends JpaRepository<Taxonomy, Long> {

    Optional<Taxonomy> findByScientificNameIgnoreCase(String scientificName);

    Page<Taxonomy> findByGenusIgnoreCase(String genus, Pageable pageable);

    /**
     * Prefix search across scientificName, genus, and commonNames.
     *
     * Postgres ILIKE is fine for our scale (a few hundred to a few
     * thousand species rows). At larger scales we'd index commonNames
     * with a Postgres trigram (`gin_trgm_ops`) or move the search to
     * OpenSearch. Spelled out as a JPQL query rather than a derived
     * one because we want OR across multiple columns.
     */
    @org.springframework.data.jpa.repository.Query("""
        select t from Taxonomy t
        where lower(t.scientificName) like lower(concat('%', :q, '%'))
           or lower(t.genus)          like lower(concat('%', :q, '%'))
           or lower(t.commonNames)    like lower(concat('%', :q, '%'))
        order by t.scientificName
    """)
    List<Taxonomy> search(@org.springframework.data.repository.query.Param("q") String q);
}

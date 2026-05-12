package com.wildme.wildbook_lite.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.FetchType;

@Entity
public class Sighting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime sightedAt;
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "encounter_id")
    private Encounter encounter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "observer_id")
    private Observer observer;

    public Sighting() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDateTime getSightedAt() { return sightedAt; }
    public void setSightedAt(LocalDateTime sightedAt) { this.sightedAt = sightedAt; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Encounter getEncounter() { return encounter; }
    public void setEncounter(Encounter encounter) { this.encounter = encounter; }

    public Observer getObserver() { return observer; }
    public void setObserver(Observer observer) { this.observer = observer; }
}

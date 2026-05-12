package com.wildme.wildbook_lite.entity;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.OneToMany;

@Entity
public class Observer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String email;
    private String organization;

    @OneToMany(mappedBy = "observer")
    private List<Encounter> encounters = new ArrayList<>();

    @OneToMany(mappedBy = "observer")
    private List<Sighting> sightings = new ArrayList<>();

    public Observer() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getOrganization() { return organization; }
    public void setOrganization(String organization) { this.organization = organization; }

    public List<Encounter> getEncounters() { return encounters; }
    public void setEncounters(List<Encounter> encounters) { this.encounters = encounters; }

    public List<Sighting> getSightings() { return sightings; }
    public void setSightings(List<Sighting> sightings) { this.sightings = sightings; }
}

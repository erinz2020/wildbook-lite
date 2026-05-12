package com.wildme.wildbook_lite.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.CascadeType;

@Entity
public class Individual {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nickname;
    private String sex;
    private String species;

    private LocalDateTime dateCreated;

    @OneToMany(mappedBy = "individual", cascade = CascadeType.ALL)
    private List<Encounter> encounters = new ArrayList<>();

    public Individual() {
        this.dateCreated = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getSex() { return sex; }
    public void setSex(String sex) { this.sex = sex; }

    public String getSpecies() { return species; }
    public void setSpecies(String species) { this.species = species; }

    public LocalDateTime getDateCreated() { return dateCreated; }
    public void setDateCreated(LocalDateTime dateCreated) { this.dateCreated = dateCreated; }

    public List<Encounter> getEncounters() { return encounters; }
    public void setEncounters(List<Encounter> encounters) { this.encounters = encounters; }
}

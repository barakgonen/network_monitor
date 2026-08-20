package com.example.tester.config;

import java.util.List;

/** Fields for both PETS_GET and PETS_CREATE - mirrors swagger/pets-demo.yml's getPet/createPet operations. */
public class PetsPayloadConfig {
    /**
     * Overrides the HTTP request method (GET/POST/PUT/DELETE/PATCH/etc) - null (the default)
     * means "use whatever's natural for the mode" (POST for PETS_CREATE, GET for PETS_GET). Set
     * this to exercise a different verb against the same path/body, e.g. to prove the monitor's
     * REST server correctly 404s a method the swagger spec doesn't define for that path.
     */
    private String method;

    // PETS_GET fields
    private String petId = "1";
    private boolean includeVaccinations = false;

    // PETS_CREATE fields
    private String name = "Rex";
    private String species = "dog";
    private int age = 3;
    private String ownerName = "Alice";
    private String ownerEmail = "alice@example.com";
    private List<String> tags = List.of("good boy");

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPetId() {
        return petId;
    }

    public void setPetId(String petId) {
        this.petId = petId;
    }

    public boolean isIncludeVaccinations() {
        return includeVaccinations;
    }

    public void setIncludeVaccinations(boolean includeVaccinations) {
        this.includeVaccinations = includeVaccinations;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSpecies() {
        return species;
    }

    public void setSpecies(String species) {
        this.species = species;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getOwnerEmail() {
        return ownerEmail;
    }

    public void setOwnerEmail(String ownerEmail) {
        this.ownerEmail = ownerEmail;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }
}

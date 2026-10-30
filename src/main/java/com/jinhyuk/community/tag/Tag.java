package com.jinhyuk.community.tag;

import java.util.Locale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "tags",
        uniqueConstraints = @UniqueConstraint(name = "uk_tags_name", columnNames = "name"))
public class Tag {

    public static final int MAX_NAME_LENGTH = 50;

    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false, unique = true, length = MAX_NAME_LENGTH)
    private String name;

    protected Tag() {
    }

    public Tag(String name) {
        String normalizedName = normalizeName(name);
        if (normalizedName.isBlank() || normalizedName.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("tag name must contain 1 to 50 characters");
        }
        this.name = normalizedName;
    }

    public static String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        return name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}

package com.jinhyuk.community.post;

import com.jinhyuk.community.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import com.jinhyuk.community.tag.Tag;
import org.hibernate.annotations.BatchSize;

@Entity
public class Post {

    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 1000)
    private String content;

    @ManyToOne(optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Enumerated(EnumType.STRING)
    @Column(name = "post_type", length = 20)
    private PostType type = PostType.STUDY_LOG;

    @ManyToMany
    @BatchSize(size = 50)
    @JoinTable(
            name = "post_tags",
            joinColumns = @JoinColumn(name = "post_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_post_tags_post_tag",
                    columnNames = {"post_id", "tag_id"}))
    private Set<Tag> tags = new LinkedHashSet<>();

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Post() {
    }

    public Post(String title, String content, User author) {
        this(title, content, author, PostType.STUDY_LOG);
    }

    public Post(String title, String content, User author, PostType type) {
        this.title = title;
        this.content = content;
        this.author = author;
        this.type = type == null ? PostType.STUDY_LOG : type;
    }

    @PrePersist
    void recordCreationTime() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (type == null) {
            type = PostType.STUDY_LOG;
        }
    }

    @PreUpdate
    void recordUpdateTime() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public User getAuthor() {
        return author;
    }

    public PostType getType() {
        return type == null ? PostType.STUDY_LOG : type;
    }

    public Set<Tag> getTags() {
        return Collections.unmodifiableSet(tags);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setType(PostType type) {
        this.type = type;
    }

    public void replaceTags(Set<Tag> replacementTags) {
        tags.clear();
        tags.addAll(replacementTags);
    }

    public void markUpdated() {
        updatedAt = Instant.now();
    }
}

package com.jinhyuk.community.post;

import java.time.Instant;
import java.util.List;

public class PostResponse {

    private final Long id;
    private final String title;
    private final String content;
    private final Long authorId;
    private final PostType type;
    private final List<String> tags;
    private final Instant createdAt;
    private final Instant updatedAt;

    private PostResponse(
            Long id,
            String title,
            String content,
            Long authorId,
            PostType type,
            List<String> tags,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.authorId = authorId;
        this.type = type;
        this.tags = tags;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static PostResponse from(Post post) {
        return new PostResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getAuthor().getId(),
                post.getType(),
                post.getTags().stream()
                        .map(tag -> tag.getName())
                        .sorted()
                        .toList(),
                post.getCreatedAt(),
                post.getUpdatedAt());
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

    public Long getAuthorId() {
        return authorId;
    }

    public PostType getType() {
        return type;
    }

    public List<String> getTags() {
        return tags;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

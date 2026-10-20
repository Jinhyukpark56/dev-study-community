package com.jinhyuk.community.comment;

import java.time.Instant;

public class CommentResponse {

    private final Long id;
    private final String content;
    private final Long postId;
    private final Long authorId;
    private final Instant createdAt;
    private final Instant updatedAt;

    private CommentResponse(
            Long id,
            String content,
            Long postId,
            Long authorId,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.content = content;
        this.postId = postId;
        this.authorId = authorId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static CommentResponse from(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getContent(),
                comment.getPost().getId(),
                comment.getAuthor().getId(),
                comment.getCreatedAt(),
                comment.getUpdatedAt());
    }

    public Long getId() {
        return id;
    }

    public String getContent() {
        return content;
    }

    public Long getPostId() {
        return postId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

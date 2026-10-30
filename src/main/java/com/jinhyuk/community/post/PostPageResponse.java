package com.jinhyuk.community.post;

import java.util.List;

import org.springframework.data.domain.Page;

public class PostPageResponse {

    private final List<PostResponse> content;
    private final int page;
    private final int size;
    private final int totalPages;
    private final long totalElements;

    private PostPageResponse(
            List<PostResponse> content,
            int page,
            int size,
            int totalPages,
            long totalElements) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalPages = totalPages;
        this.totalElements = totalElements;
    }

    public static PostPageResponse from(Page<Post> posts) {
        return new PostPageResponse(
                posts.getContent().stream().map(PostResponse::from).toList(),
                posts.getNumber(),
                posts.getSize(),
                posts.getTotalPages(),
                posts.getTotalElements());
    }

    public List<PostResponse> getContent() {
        return content;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public long getTotalElements() {
        return totalElements;
    }
}

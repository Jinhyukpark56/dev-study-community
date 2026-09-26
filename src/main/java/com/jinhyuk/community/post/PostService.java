package com.jinhyuk.community.post;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostService {

    private final PostRepository postRepository;

    public PostService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    @Transactional
    public boolean addPost(Post post) {
        if (!isValidPost(post) || post.getId() != null) {
            return false;
        }

        postRepository.save(post);
        return true;
    }

    public Post findPostById(Long targetId) {
        if (targetId == null) {
            return null;
        }

        return postRepository.findById(targetId).orElse(null);
    }

    public List<Post> findAllPosts() {
        return postRepository.findAll();
    }

    @Transactional
    public boolean updatePost(Long targetId, String title, String content) {
        // 두 값을 모두 확인한 뒤 수정하여 실패 시 기존 내용을 유지한다.
        if (targetId == null || !isValidText(title, content)) {
            return false;
        }

        Post post = postRepository.findById(targetId).orElse(null);
        if (post == null) {
            return false;
        }

        post.setTitle(title);
        post.setContent(content);
        return true;
    }

    @Transactional
    public boolean deletePost(Long targetId) {
        if (targetId == null) {
            return false;
        }

        Post post = postRepository.findById(targetId).orElse(null);
        if (post == null) {
            return false;
        }

        postRepository.delete(post);
        return true;
    }

    private boolean isValidPost(Post post) {
        return post != null && isValidText(post.getTitle(), post.getContent());
    }

    private boolean isValidText(String title, String content) {
        if (title == null || title.isBlank() || title.length() > 100) {
            return false;
        }
        if (content == null || content.isBlank() || content.length() > 1000) {
            return false;
        }
        return true;
    }
}

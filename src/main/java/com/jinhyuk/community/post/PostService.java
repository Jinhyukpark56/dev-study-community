package com.jinhyuk.community.post;

import java.util.List;

import com.jinhyuk.community.user.User;
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
    public PostOperationResult updatePost(
            Long targetId,
            User currentUser,
            String title,
            String content) {
        if (targetId == null) {
            return PostOperationResult.NOT_FOUND;
        }
        Post post = postRepository.findById(targetId).orElse(null);
        if (post == null) {
            return PostOperationResult.NOT_FOUND;
        }
        if (!isAuthor(post, currentUser)) {
            return PostOperationResult.FORBIDDEN;
        }
        // 권한과 두 입력값을 모두 확인한 뒤 수정하여 실패 시 기존 내용을 유지한다.
        if (!isValidText(title, content)) {
            return PostOperationResult.INVALID_INPUT;
        }

        post.setTitle(title);
        post.setContent(content);
        return PostOperationResult.SUCCESS;
    }

    @Transactional
    public PostOperationResult deletePost(Long targetId, User currentUser) {
        if (targetId == null) {
            return PostOperationResult.NOT_FOUND;
        }
        Post post = postRepository.findById(targetId).orElse(null);
        if (post == null) {
            return PostOperationResult.NOT_FOUND;
        }
        if (!isAuthor(post, currentUser)) {
            return PostOperationResult.FORBIDDEN;
        }

        postRepository.delete(post);
        return PostOperationResult.SUCCESS;
    }

    private boolean isValidPost(Post post) {
        return post != null
                && post.getAuthor() != null
                && post.getAuthor().getId() != null
                && isValidText(post.getTitle(), post.getContent());
    }

    private boolean isAuthor(Post post, User currentUser) {
        if (post.getAuthor() == null
                || post.getAuthor().getId() == null
                || currentUser == null
                || currentUser.getId() == null) {
            return false;
        }
        return post.getAuthor().getId().equals(currentUser.getId());
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

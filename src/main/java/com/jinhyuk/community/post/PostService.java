package com.jinhyuk.community.post;

import java.util.ArrayList;
import java.util.List;

// 학습용 메모리 저장소: 서비스 객체마다 별도 목록을 가지며 재시작하면 사라진다.
public class PostService {

    private List<Post> posts = new ArrayList<>();

    public boolean addPost(Post post) {
        if (!isValidPost(post) || findStoredPostById(post.getId()) != null) {
            return false;
        }

        // 호출자가 원본 객체를 수정해 저장소의 검증을 우회하지 않도록 복사한다.
        posts.add(new Post(post.getId(), post.getTitle(), post.getContent()));
        return true;
    }

    public Post findPostById(int targetId) {
        Post post = findStoredPostById(targetId);
        if (post == null) {
            return null;
        }

        return new Post(post.getId(), post.getTitle(), post.getContent());
    }

    public boolean updatePost(int targetId, String title, String content) {
        // 두 값을 모두 확인한 뒤 수정하여 실패 시 기존 내용을 유지한다.
        if (!isValidText(title, content)) {
            return false;
        }

        Post post = findStoredPostById(targetId);
        if (post == null) {
            return false;
        }

        post.setTitle(title);
        post.setContent(content);
        return true;
    }

    public boolean deletePost(int targetId) {
        Post post = findStoredPostById(targetId);
        if (post == null) {
            return false;
        }

        posts.remove(post);
        return true;
    }

    private Post findStoredPostById(int targetId) {
        for (Post post : posts) {
            if (post.getId() == targetId) {
                return post;
            }
        }
        return null;
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

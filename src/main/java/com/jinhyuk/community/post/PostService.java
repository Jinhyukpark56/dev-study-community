package com.jinhyuk.community.post;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.jinhyuk.community.comment.CommentRepository;
import com.jinhyuk.community.tag.Tag;
import com.jinhyuk.community.tag.TagRepository;
import com.jinhyuk.community.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostService {

    private static final int MAX_TAGS_PER_POST = 3;

    private final PostRepository postRepository;
    private final TagRepository tagRepository;
    private final CommentRepository commentRepository;

    public PostService(
            PostRepository postRepository,
            TagRepository tagRepository,
            CommentRepository commentRepository) {
        this.postRepository = postRepository;
        this.tagRepository = tagRepository;
        this.commentRepository = commentRepository;
    }

    @Transactional
    public boolean addPost(Post post) {
        return addPost(post, List.of());
    }

    @Transactional
    public boolean addPost(Post post, Collection<String> tagNames) {
        if (!isValidPost(post) || post.getId() != null) {
            return false;
        }

        Set<String> normalizedNames = normalizeTagNames(tagNames);
        if (normalizedNames == null) {
            return false;
        }

        post.replaceTags(resolveTags(normalizedNames));
        postRepository.save(post);
        return true;
    }

    @Transactional(readOnly = true)
    public Post findPostById(Long targetId) {
        if (targetId == null) {
            return null;
        }

        return postRepository.findById(targetId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<Post> findAllPosts() {
        List<Post> posts = postRepository.findAll();
        posts.forEach(post -> post.getTags().size());
        return posts;
    }

    @Transactional(readOnly = true)
    public Page<Post> findPosts(int page, int size, String tagName) {
        if (page < 0 || size < 1 || size > 50) {
            throw new IllegalArgumentException("page must be at least 0 and size must be between 1 and 50");
        }

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("createdAt").nullsLast(),
                        Sort.Order.desc("id")));
        Page<Post> posts;
        if (tagName == null || tagName.isBlank()) {
            posts = postRepository.findAll(pageable);
        } else {
            String normalizedTagName = Tag.normalizeName(tagName);
            if (normalizedTagName.length() > Tag.MAX_NAME_LENGTH) {
                throw new IllegalArgumentException("tag is too long");
            }
            posts = postRepository.findAllByTagName(normalizedTagName, pageable);
        }

        posts.getContent().forEach(post -> post.getTags().size());
        return posts;
    }

    @Transactional
    public PostOperationResult updatePost(
            Long targetId,
            User currentUser,
            String title,
            String content) {
        return updatePost(targetId, currentUser, title, content, null, null);
    }

    @Transactional
    public PostOperationResult updatePost(
            Long targetId,
            User currentUser,
            String title,
            String content,
            PostType type,
            Collection<String> tagNames) {
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

        Set<String> normalizedNames = null;
        if (tagNames != null) {
            normalizedNames = normalizeTagNames(tagNames);
            if (normalizedNames == null) {
                return PostOperationResult.INVALID_INPUT;
            }
        }

        post.setTitle(title);
        post.setContent(content);
        if (type != null) {
            post.setType(type);
        }
        if (normalizedNames != null) {
            post.replaceTags(resolveTags(normalizedNames));
        }
        post.markUpdated();
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

        // 댓글과 게시글을 같은 transaction에서 지워 중간 상태가 DB에 남지 않게 한다.
        commentRepository.deleteAllByPostId(post.getId());
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

    private Set<String> normalizeTagNames(Collection<String> tagNames) {
        if (tagNames == null) {
            return Set.of();
        }

        Set<String> normalizedNames = new LinkedHashSet<>();
        for (String tagName : tagNames) {
            String normalizedName = Tag.normalizeName(tagName);
            if (normalizedName.isBlank()) {
                continue;
            }
            if (normalizedName.length() > Tag.MAX_NAME_LENGTH) {
                return null;
            }
            normalizedNames.add(normalizedName);
        }
        if (normalizedNames.size() > MAX_TAGS_PER_POST) {
            return null;
        }
        return normalizedNames;
    }

    private Set<Tag> resolveTags(Set<String> normalizedNames) {
        Set<Tag> tags = new LinkedHashSet<>();
        for (String normalizedName : normalizedNames) {
            Tag tag = tagRepository.findByName(normalizedName)
                    .orElseGet(() -> tagRepository.save(new Tag(normalizedName)));
            tags.add(tag);
        }
        return tags;
    }
}

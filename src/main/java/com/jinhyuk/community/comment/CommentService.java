package com.jinhyuk.community.comment;

import java.util.List;

import com.jinhyuk.community.post.Post;
import com.jinhyuk.community.post.PostRepository;
import com.jinhyuk.community.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {

    private static final int MAX_CONTENT_LENGTH = 1000;

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;

    public CommentService(CommentRepository commentRepository, PostRepository postRepository) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
    }

    @Transactional(readOnly = true)
    public List<Comment> findCommentsByPostId(Long postId) {
        if (postId == null || !postRepository.existsById(postId)) {
            return null;
        }
        return commentRepository.findByPostIdOrderByCreatedAtAscIdAsc(postId);
    }

    @Transactional(readOnly = true)
    public Comment findComment(Long postId, Long commentId) {
        if (postId == null || commentId == null) {
            return null;
        }
        return commentRepository.findByIdAndPostId(commentId, postId).orElse(null);
    }

    @Transactional
    public CommentCreationResult addComment(Long postId, User currentUser, String content) {
        if (postId == null) {
            return CommentCreationResult.failure(CommentOperationResult.NOT_FOUND);
        }
        Post post = postRepository.findById(postId).orElse(null);
        if (post == null) {
            return CommentCreationResult.failure(CommentOperationResult.NOT_FOUND);
        }
        if (!isSavedUser(currentUser)) {
            return CommentCreationResult.failure(CommentOperationResult.FORBIDDEN);
        }
        if (!isValidContent(content)) {
            return CommentCreationResult.failure(CommentOperationResult.INVALID_INPUT);
        }

        Comment comment = commentRepository.save(new Comment(content, post, currentUser));
        return CommentCreationResult.success(comment);
    }

    @Transactional
    public CommentOperationResult updateComment(
            Long postId,
            Long commentId,
            User currentUser,
            String content) {
        Comment comment = findCommentForChange(postId, commentId);
        if (comment == null) {
            return CommentOperationResult.NOT_FOUND;
        }
        if (!isAuthor(comment, currentUser)) {
            return CommentOperationResult.FORBIDDEN;
        }
        if (!isValidContent(content)) {
            return CommentOperationResult.INVALID_INPUT;
        }

        comment.updateContent(content);
        return CommentOperationResult.SUCCESS;
    }

    @Transactional
    public CommentOperationResult deleteComment(Long postId, Long commentId, User currentUser) {
        Comment comment = findCommentForChange(postId, commentId);
        if (comment == null) {
            return CommentOperationResult.NOT_FOUND;
        }
        if (!isAuthor(comment, currentUser)) {
            return CommentOperationResult.FORBIDDEN;
        }

        commentRepository.delete(comment);
        return CommentOperationResult.SUCCESS;
    }

    private Comment findCommentForChange(Long postId, Long commentId) {
        if (postId == null || commentId == null) {
            return null;
        }
        return commentRepository.findByIdAndPostId(commentId, postId).orElse(null);
    }

    private boolean isAuthor(Comment comment, User currentUser) {
        return isSavedUser(currentUser)
                && comment.getAuthor() != null
                && comment.getAuthor().getId() != null
                && comment.getAuthor().getId().equals(currentUser.getId());
    }

    private boolean isSavedUser(User user) {
        return user != null && user.getId() != null;
    }

    private boolean isValidContent(String content) {
        return content != null
                && !content.isBlank()
                && content.length() <= MAX_CONTENT_LENGTH;
    }
}

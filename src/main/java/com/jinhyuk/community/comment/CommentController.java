package com.jinhyuk.community.comment;

import java.net.URI;
import java.util.List;

import com.jinhyuk.community.user.User;
import com.jinhyuk.community.user.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/posts/{postId}/comments")
public class CommentController {

    private final CommentService commentService;
    private final UserService userService;

    public CommentController(CommentService commentService, UserService userService) {
        this.commentService = commentService;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<CommentResponse>> findComments(@PathVariable Long postId) {
        List<Comment> comments = commentService.findCommentsByPostId(postId);
        if (comments == null) {
            return ResponseEntity.notFound().build();
        }

        List<CommentResponse> responses = comments.stream()
                .map(CommentResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{commentId}")
    public ResponseEntity<CommentResponse> findComment(
            @PathVariable Long postId,
            @PathVariable Long commentId) {
        Comment comment = commentService.findComment(postId, commentId);
        if (comment == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(CommentResponse.from(comment));
    }

    @PostMapping
    public ResponseEntity<Void> createComment(
            @PathVariable Long postId,
            @RequestParam String content,
            Authentication authentication) {
        User currentUser = findCurrentUser(authentication);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        CommentCreationResult creation = commentService.addComment(postId, currentUser, content);
        if (creation.getResult() == CommentOperationResult.SUCCESS) {
            URI location = URI.create(
                    "/posts/" + postId + "/comments/" + creation.getComment().getId());
            return ResponseEntity.created(location).build();
        }
        return responseFor(creation.getResult(), HttpStatus.CREATED);
    }

    @PatchMapping("/{commentId}")
    public ResponseEntity<Void> updateComment(
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @RequestParam String content,
            Authentication authentication) {
        User currentUser = findCurrentUser(authentication);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        CommentOperationResult result = commentService.updateComment(
                postId,
                commentId,
                currentUser,
                content);
        return responseFor(result, HttpStatus.OK);
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long postId,
            @PathVariable Long commentId,
            Authentication authentication) {
        User currentUser = findCurrentUser(authentication);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        CommentOperationResult result = commentService.deleteComment(
                postId,
                commentId,
                currentUser);
        return responseFor(result, HttpStatus.NO_CONTENT);
    }

    private User findCurrentUser(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        return userService.findByEmail(authentication.getName()).orElse(null);
    }

    private ResponseEntity<Void> responseFor(
            CommentOperationResult result,
            HttpStatus successStatus) {
        return switch (result) {
            case SUCCESS -> ResponseEntity.status(successStatus).build();
            case INVALID_INPUT -> ResponseEntity.badRequest().build();
            case NOT_FOUND -> ResponseEntity.notFound().build();
            case FORBIDDEN -> ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        };
    }
}

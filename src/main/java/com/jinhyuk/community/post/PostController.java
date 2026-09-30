package com.jinhyuk.community.post;

import java.net.URI;
import java.util.ArrayList;
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
@RequestMapping("/posts")
public class PostController {

    private final PostService postService;
    private final UserService userService;

    public PostController(PostService postService, UserService userService) {
        this.postService = postService;
        this.userService = userService;
    }

    @GetMapping
    public List<PostResponse> findAllPosts() {
        List<PostResponse> responses = new ArrayList<>();
        for (Post post : postService.findAllPosts()) {
            responses.add(PostResponse.from(post));
        }
        return responses;
    }

    @GetMapping("/{id}")
    public ResponseEntity<PostResponse> findPost(@PathVariable Long id) {
        Post post = postService.findPostById(id);
        if (post == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(PostResponse.from(post));
    }

    @PostMapping
    public ResponseEntity<Void> createPost(
            @RequestParam String title,
            @RequestParam String content,
            Authentication authentication) {
        User currentUser = findCurrentUser(authentication);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Post post = new Post(title, content, currentUser);
        if (!postService.addPost(post)) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.created(URI.create("/posts/" + post.getId())).build();
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> updatePost(
            @PathVariable Long id,
            @RequestParam String title,
            @RequestParam String content,
            Authentication authentication) {
        User currentUser = findCurrentUser(authentication);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        PostOperationResult result = postService.updatePost(id, currentUser, title, content);
        return responseFor(result, HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(
            @PathVariable Long id,
            Authentication authentication) {
        User currentUser = findCurrentUser(authentication);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        PostOperationResult result = postService.deletePost(id, currentUser);
        return responseFor(result, HttpStatus.NO_CONTENT);
    }

    private User findCurrentUser(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        return userService.findByEmail(authentication.getName()).orElse(null);
    }

    private ResponseEntity<Void> responseFor(PostOperationResult result, HttpStatus successStatus) {
        return switch (result) {
            case SUCCESS -> ResponseEntity.status(successStatus).build();
            case INVALID_INPUT -> ResponseEntity.badRequest().build();
            case NOT_FOUND -> ResponseEntity.notFound().build();
            case FORBIDDEN -> ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        };
    }
}

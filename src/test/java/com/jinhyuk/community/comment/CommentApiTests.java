package com.jinhyuk.community.comment;

import com.jinhyuk.community.post.Post;
import com.jinhyuk.community.post.PostRepository;
import com.jinhyuk.community.user.User;
import com.jinhyuk.community.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CommentApiTests {

    private static final String OWNER_EMAIL = "comment-owner@example.com";
    private static final String OTHER_EMAIL = "comment-other@example.com";
    private static final String PASSWORD = "correct-password";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        commentRepository.deleteAll();
        postRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void anyoneCanReadCommentsInStableOrderWithoutPasswordExposure() throws Exception {
        registerAndLogin(OWNER_EMAIL);
        User owner = findUser(OWNER_EMAIL);
        Post post = savePost(owner, "공개 글");
        Comment first = commentRepository.save(new Comment("첫 댓글", post, owner));
        Comment second = commentRepository.save(new Comment("둘째 댓글", post, owner));

        mvc.perform(get("/posts/{postId}/comments", post.getId()))
                .andExpect(status().isOk())
                .andExpect(unauthenticated())
                .andExpect(jsonPath("$[0].id").value(first.getId()))
                .andExpect(jsonPath("$[0].content").value("첫 댓글"))
                .andExpect(jsonPath("$[0].postId").value(post.getId()))
                .andExpect(jsonPath("$[0].authorId").value(owner.getId()))
                .andExpect(jsonPath("$[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$[0].updatedAt").isNotEmpty())
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[1].id").value(second.getId()));

        mvc.perform(get(
                        "/posts/{postId}/comments/{commentId}",
                        post.getId(),
                        first.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("첫 댓글"));
    }

    @Test
    void readingCommentsOfMissingPostReturnsNotFound() throws Exception {
        mvc.perform(get("/posts/{postId}/comments", 999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedUserCannotCreateUpdateOrDeleteComment() throws Exception {
        mvc.perform(post("/posts/{postId}/comments", 999L)
                        .with(csrf())
                        .param("content", "댓글"))
                .andExpect(status().isUnauthorized())
                .andExpect(unauthenticated());
        mvc.perform(patch("/posts/{postId}/comments/{commentId}", 999L, 999L)
                        .with(csrf())
                        .param("content", "수정 댓글"))
                .andExpect(status().isUnauthorized())
                .andExpect(unauthenticated());
        mvc.perform(delete("/posts/{postId}/comments/{commentId}", 999L, 999L)
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(unauthenticated());
    }

    @Test
    void authenticatedUserCreatesCommentAsSelf() throws Exception {
        registerAndLogin(OTHER_EMAIL);
        User other = findUser(OTHER_EMAIL);
        MockHttpSession ownerSession = registerAndLogin(OWNER_EMAIL);
        User owner = findUser(OWNER_EMAIL);
        Post post = savePost(owner, "댓글을 받을 글");

        mvc.perform(post("/posts/{postId}/comments", post.getId())
                        .session(ownerSession)
                        .with(csrf())
                        .param("content", "작성자 자동 지정")
                        .param("authorId", other.getId().toString()))
                .andExpect(status().isCreated())
                .andExpect(authenticated().withUsername(OWNER_EMAIL))
                .andExpect(header().string(
                        "Location",
                        org.hamcrest.Matchers.matchesPattern(
                                "/posts/" + post.getId() + "/comments/\\d+")));

        Comment saved = commentRepository.findAll().get(0);
        assertEquals(owner.getId(), saved.getAuthor().getId());
        assertEquals(post.getId(), saved.getPost().getId());
    }

    @Test
    void invalidContentIsRejectedAndMissingPostReturnsNotFound() throws Exception {
        MockHttpSession ownerSession = registerAndLogin(OWNER_EMAIL);
        User owner = findUser(OWNER_EMAIL);
        Post post = savePost(owner, "댓글을 받을 글");

        mvc.perform(post("/posts/{postId}/comments", post.getId())
                        .session(ownerSession)
                        .with(csrf())
                        .param("content", " "))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/posts/{postId}/comments", post.getId())
                        .session(ownerSession)
                        .with(csrf())
                        .param("content", "가".repeat(1001)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/posts/{postId}/comments", 999L)
                        .session(ownerSession)
                        .with(csrf())
                        .param("content", "댓글"))
                .andExpect(status().isNotFound());

        assertEquals(0, commentRepository.count());
    }

    @Test
    void authorCanUpdateOwnComment() throws Exception {
        MockHttpSession ownerSession = registerAndLogin(OWNER_EMAIL);
        User owner = findUser(OWNER_EMAIL);
        Post post = savePost(owner, "글");
        Comment comment = commentRepository.save(new Comment("이전 댓글", post, owner));

        mvc.perform(patch(
                        "/posts/{postId}/comments/{commentId}",
                        post.getId(),
                        comment.getId())
                        .session(ownerSession)
                        .with(csrf())
                        .param("content", "수정 댓글"))
                .andExpect(status().isOk());

        Comment updated = commentRepository.findById(comment.getId()).orElseThrow();
        assertEquals("수정 댓글", updated.getContent());
    }

    @Test
    void otherUserCannotUpdateOrDeleteComment() throws Exception {
        registerAndLogin(OWNER_EMAIL);
        User owner = findUser(OWNER_EMAIL);
        Post post = savePost(owner, "글");
        Comment comment = commentRepository.save(new Comment("보존할 댓글", post, owner));
        MockHttpSession otherSession = registerAndLogin(OTHER_EMAIL);

        mvc.perform(patch(
                        "/posts/{postId}/comments/{commentId}",
                        post.getId(),
                        comment.getId())
                        .session(otherSession)
                        .with(csrf())
                        .param("content", " "))
                .andExpect(status().isForbidden());
        mvc.perform(delete(
                        "/posts/{postId}/comments/{commentId}",
                        post.getId(),
                        comment.getId())
                        .session(otherSession)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        assertEquals("보존할 댓글", commentRepository.findById(comment.getId()).orElseThrow().getContent());
    }

    @Test
    void commentUnderDifferentPostIsNotFound() throws Exception {
        MockHttpSession ownerSession = registerAndLogin(OWNER_EMAIL);
        User owner = findUser(OWNER_EMAIL);
        Post actualPost = savePost(owner, "실제 글");
        Post urlPost = savePost(owner, "URL의 다른 글");
        Comment comment = commentRepository.save(new Comment("댓글", actualPost, owner));

        mvc.perform(get(
                        "/posts/{postId}/comments/{commentId}",
                        urlPost.getId(),
                        comment.getId()))
                .andExpect(status().isNotFound());
        mvc.perform(patch(
                        "/posts/{postId}/comments/{commentId}",
                        urlPost.getId(),
                        comment.getId())
                        .session(ownerSession)
                        .with(csrf())
                        .param("content", "수정하면 안 됨"))
                .andExpect(status().isNotFound());
        mvc.perform(delete(
                        "/posts/{postId}/comments/{commentId}",
                        urlPost.getId(),
                        comment.getId())
                        .session(ownerSession)
                        .with(csrf()))
                .andExpect(status().isNotFound());

        assertTrue(commentRepository.existsById(comment.getId()));
    }

    @Test
    void authorDeletesCommentWithoutDeletingPostOrUser() throws Exception {
        MockHttpSession ownerSession = registerAndLogin(OWNER_EMAIL);
        User owner = findUser(OWNER_EMAIL);
        Post post = savePost(owner, "유지할 글");
        Comment comment = commentRepository.save(new Comment("삭제할 댓글", post, owner));

        mvc.perform(delete(
                        "/posts/{postId}/comments/{commentId}",
                        post.getId(),
                        comment.getId())
                        .session(ownerSession)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertFalse(commentRepository.existsById(comment.getId()));
        assertTrue(postRepository.existsById(post.getId()));
        assertTrue(userRepository.existsById(owner.getId()));
    }

    @Test
    void missingCsrfReturnsForbiddenWithoutChangingComment() throws Exception {
        MockHttpSession ownerSession = registerAndLogin(OWNER_EMAIL);
        User owner = findUser(OWNER_EMAIL);
        Post post = savePost(owner, "글");
        Comment comment = commentRepository.save(new Comment("원래 댓글", post, owner));

        mvc.perform(patch(
                        "/posts/{postId}/comments/{commentId}",
                        post.getId(),
                        comment.getId())
                        .session(ownerSession)
                        .param("content", "바뀌면 안 됨"))
                .andExpect(status().isForbidden());

        assertEquals("원래 댓글", commentRepository.findById(comment.getId()).orElseThrow().getContent());
    }

    private MockHttpSession registerAndLogin(String email) throws Exception {
        mvc.perform(post("/auth/register")
                        .with(csrf())
                        .param("email", email)
                        .param("password", PASSWORD))
                .andExpect(status().isCreated());

        MvcResult loginResult = mvc.perform(post("/auth/login")
                        .with(csrf())
                        .param("email", email)
                        .param("password", PASSWORD))
                .andExpect(status().isOk())
                .andReturn();

        return (MockHttpSession) loginResult.getRequest().getSession(false);
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email).orElseThrow();
    }

    private Post savePost(User author, String title) {
        return postRepository.save(new Post(title, "본문", author));
    }
}

package com.jinhyuk.community.post;

import com.jinhyuk.community.user.User;
import com.jinhyuk.community.user.UserRepository;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
class PostAuthorizationTests {

    private static final String OWNER_EMAIL = "owner@example.com";
    private static final String OTHER_EMAIL = "other@example.com";
    private static final String PASSWORD = "correct-password";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void clearDatabase() {
        postRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void unauthenticatedUserCannotCreateUpdateOrDeletePost() throws Exception {
        mvc.perform(post("/posts")
                        .with(csrf())
                        .param("title", "제목")
                        .param("content", "내용"))
                .andExpect(status().isUnauthorized())
                .andExpect(unauthenticated());
        mvc.perform(patch("/posts/{id}", 999L)
                        .with(csrf())
                        .param("title", "수정 제목")
                        .param("content", "수정 내용"))
                .andExpect(status().isUnauthorized())
                .andExpect(unauthenticated());
        mvc.perform(delete("/posts/{id}", 999L).with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(unauthenticated());

        assertEquals(0, postRepository.count());
    }

    @Test
    void authenticatedUserCreatesPostAsSelfEvenWithSpoofedAuthorId() throws Exception {
        registerAndLogin(OTHER_EMAIL);
        User other = findUser(OTHER_EMAIL);
        MockHttpSession ownerSession = registerAndLogin(OWNER_EMAIL);
        User owner = findUser(OWNER_EMAIL);

        mvc.perform(post("/posts")
                        .session(ownerSession)
                        .with(csrf())
                        .param("title", "작성자 자동 지정")
                        .param("content", "클라이언트 authorId는 사용하지 않는다")
                        .param("authorId", other.getId().toString()))
                .andExpect(status().isCreated())
                .andExpect(authenticated().withUsername(OWNER_EMAIL))
                .andExpect(header().exists("Location"));

        Post saved = postRepository.findAll().get(0);
        assertEquals(owner.getId(), saved.getAuthor().getId());
        assertNotEquals(other.getId(), saved.getAuthor().getId());
    }

    @Test
    void invalidPostCreationReturnsBadRequestWithoutSavingPost() throws Exception {
        MockHttpSession ownerSession = registerAndLogin(OWNER_EMAIL);

        mvc.perform(post("/posts")
                        .session(ownerSession)
                        .with(csrf())
                        .param("title", " ")
                        .param("content", "내용"))
                .andExpect(status().isBadRequest())
                .andExpect(authenticated().withUsername(OWNER_EMAIL));

        assertEquals(0, postRepository.count());
    }

    @Test
    void unauthenticatedUserCanReadPostsWithoutPasswordHashExposure() throws Exception {
        registerAndLogin(OWNER_EMAIL);
        User owner = findUser(OWNER_EMAIL);
        Post saved = savePost(owner, "공개 제목", "공개 내용");

        mvc.perform(get("/posts/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(unauthenticated())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.title").value("공개 제목"))
                .andExpect(jsonPath("$.content").value("공개 내용"))
                .andExpect(jsonPath("$.authorId").value(owner.getId()))
                .andExpect(jsonPath("$.password").doesNotExist());
        mvc.perform(get("/posts"))
                .andExpect(status().isOk())
                .andExpect(unauthenticated())
                .andExpect(jsonPath("$[0].authorId").value(owner.getId()))
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    void authorCanUpdateOwnPost() throws Exception {
        MockHttpSession ownerSession = registerAndLogin(OWNER_EMAIL);
        User owner = findUser(OWNER_EMAIL);
        Post saved = savePost(owner, "이전 제목", "이전 내용");

        mvc.perform(patch("/posts/{id}", saved.getId())
                        .session(ownerSession)
                        .with(csrf())
                        .param("title", "새 제목")
                        .param("content", "새 내용"))
                .andExpect(status().isOk())
                .andExpect(authenticated().withUsername(OWNER_EMAIL));

        Post updated = postRepository.findById(saved.getId()).orElseThrow();
        assertEquals("새 제목", updated.getTitle());
        assertEquals("새 내용", updated.getContent());
        assertEquals(owner.getId(), updated.getAuthor().getId());
    }

    @Test
    void invalidUpdateReturnsBadRequestWithoutPartialChanges() throws Exception {
        MockHttpSession ownerSession = registerAndLogin(OWNER_EMAIL);
        User owner = findUser(OWNER_EMAIL);
        Post saved = savePost(owner, "원래 제목", "원래 내용");

        mvc.perform(patch("/posts/{id}", saved.getId())
                        .session(ownerSession)
                        .with(csrf())
                        .param("title", "바뀌면 안 되는 제목")
                        .param("content", " "))
                .andExpect(status().isBadRequest())
                .andExpect(authenticated().withUsername(OWNER_EMAIL));

        Post unchanged = postRepository.findById(saved.getId()).orElseThrow();
        assertEquals("원래 제목", unchanged.getTitle());
        assertEquals("원래 내용", unchanged.getContent());
    }

    @Test
    void otherUserCannotUpdatePostAndOriginalValuesRemain() throws Exception {
        registerAndLogin(OWNER_EMAIL);
        User owner = findUser(OWNER_EMAIL);
        Post saved = savePost(owner, "원래 제목", "원래 내용");
        MockHttpSession otherSession = registerAndLogin(OTHER_EMAIL);

        mvc.perform(patch("/posts/{id}", saved.getId())
                        .session(otherSession)
                        .with(csrf())
                        .param("title", "권한 없는 제목")
                        .param("content", "권한 없는 내용"))
                .andExpect(status().isForbidden())
                .andExpect(authenticated().withUsername(OTHER_EMAIL));

        Post unchanged = postRepository.findById(saved.getId()).orElseThrow();
        assertEquals("원래 제목", unchanged.getTitle());
        assertEquals("원래 내용", unchanged.getContent());
        assertEquals(owner.getId(), unchanged.getAuthor().getId());
    }

    @Test
    void authorizationIsCheckedBeforeUpdateValidation() throws Exception {
        registerAndLogin(OWNER_EMAIL);
        User owner = findUser(OWNER_EMAIL);
        Post saved = savePost(owner, "원래 제목", "원래 내용");
        MockHttpSession otherSession = registerAndLogin(OTHER_EMAIL);

        mvc.perform(patch("/posts/{id}", saved.getId())
                        .session(otherSession)
                        .with(csrf())
                        .param("title", " ")
                        .param("content", " "))
                .andExpect(status().isForbidden())
                .andExpect(authenticated().withUsername(OTHER_EMAIL));

        Post unchanged = postRepository.findById(saved.getId()).orElseThrow();
        assertEquals("원래 제목", unchanged.getTitle());
        assertEquals("원래 내용", unchanged.getContent());
    }

    @Test
    void updatingMissingPostReturnsNotFound() throws Exception {
        MockHttpSession ownerSession = registerAndLogin(OWNER_EMAIL);

        mvc.perform(patch("/posts/{id}", 999L)
                        .session(ownerSession)
                        .with(csrf())
                        .param("title", "제목")
                        .param("content", "내용"))
                .andExpect(status().isNotFound());
    }

    @Test
    void authorCanDeleteOwnPost() throws Exception {
        MockHttpSession ownerSession = registerAndLogin(OWNER_EMAIL);
        User owner = findUser(OWNER_EMAIL);
        Post saved = savePost(owner, "삭제할 제목", "삭제할 내용");

        mvc.perform(delete("/posts/{id}", saved.getId())
                        .session(ownerSession)
                        .with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(authenticated().withUsername(OWNER_EMAIL));

        assertFalse(postRepository.existsById(saved.getId()));
    }

    @Test
    void otherUserCannotDeletePostAndPostRemains() throws Exception {
        registerAndLogin(OWNER_EMAIL);
        User owner = findUser(OWNER_EMAIL);
        Post saved = savePost(owner, "보존할 제목", "보존할 내용");
        MockHttpSession otherSession = registerAndLogin(OTHER_EMAIL);

        mvc.perform(delete("/posts/{id}", saved.getId())
                        .session(otherSession)
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(authenticated().withUsername(OTHER_EMAIL));

        Post unchanged = postRepository.findById(saved.getId()).orElseThrow();
        assertEquals("보존할 제목", unchanged.getTitle());
        assertEquals("보존할 내용", unchanged.getContent());
        assertEquals(owner.getId(), unchanged.getAuthor().getId());
    }

    @Test
    void deletingMissingPostReturnsNotFound() throws Exception {
        MockHttpSession ownerSession = registerAndLogin(OWNER_EMAIL);

        mvc.perform(delete("/posts/{id}", 999L)
                        .session(ownerSession)
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingCsrfReturnsForbiddenWithoutChangingPost() throws Exception {
        MockHttpSession ownerSession = registerAndLogin(OWNER_EMAIL);
        User owner = findUser(OWNER_EMAIL);
        Post saved = savePost(owner, "원래 제목", "원래 내용");

        mvc.perform(patch("/posts/{id}", saved.getId())
                        .session(ownerSession)
                        .param("title", "바뀌면 안 되는 제목")
                        .param("content", "바뀌면 안 되는 내용"))
                .andExpect(status().isForbidden());

        Post unchanged = postRepository.findById(saved.getId()).orElseThrow();
        assertEquals("원래 제목", unchanged.getTitle());
        assertEquals("원래 내용", unchanged.getContent());
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
                .andExpect(authenticated().withUsername(email))
                .andReturn();

        return (MockHttpSession) loginResult.getRequest().getSession(false);
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email).orElseThrow();
    }

    private Post savePost(User author, String title, String content) {
        return postRepository.save(new Post(title, content, author));
    }
}

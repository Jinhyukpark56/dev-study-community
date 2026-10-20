package com.jinhyuk.community.post;

import java.util.List;
import java.util.Set;

import com.jinhyuk.community.tag.TagRepository;
import com.jinhyuk.community.user.User;
import com.jinhyuk.community.user.UserRepository;
import com.jinhyuk.community.user.UserService;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PostTagPaginationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PostService postService;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    private User author;

    @BeforeEach
    void clearDatabase() {
        postRepository.deleteAll();
        tagRepository.deleteAll();
        userRepository.deleteAll();
        assertTrue(userService.register("tag-author@example.com", "author-password"));
        author = userRepository.findByEmail("tag-author@example.com").orElseThrow();
    }

    @Test
    void normalizesAndReusesTagsAndRejectsMoreThanThreeDistinctTags() {
        Post first = new Post("첫 글", "첫 내용", author, PostType.QUESTION);
        Post second = new Post("둘째 글", "둘째 내용", author);

        assertTrue(postService.addPost(first, List.of(" Java ", "SPRING", "java")));
        assertTrue(postService.addPost(second, List.of(" JAVA ")));
        assertFalse(postService.addPost(
                new Post("태그 초과", "저장되면 안 됨", author),
                List.of("one", "two", "three", "four")));

        assertEquals(2, tagRepository.count());
        assertEquals(Set.of("java", "spring"), tagNames(first));
        assertEquals(Set.of("java"), tagNames(second));
        assertEquals(PostType.QUESTION, first.getType());
        assertNotNull(first.getCreatedAt());
        assertNotNull(first.getUpdatedAt());
        assertEquals(2, postRepository.count());
    }

    @Test
    void updatesTypeAndTagRelationshipsWithoutDeletingSharedTags() {
        Post first = register("첫 글", "첫 내용", PostType.STUDY_LOG, List.of("java"));
        Post second = register("둘째 글", "둘째 내용", PostType.STUDY_LOG, List.of("java"));

        assertEquals(
                PostOperationResult.SUCCESS,
                postService.updatePost(
                        first.getId(),
                        author,
                        "수정 글",
                        "수정 내용",
                        PostType.QUESTION,
                        List.of("JPA")));
        postRepository.flush();

        Post updated = postService.findPostById(first.getId());
        assertEquals(PostType.QUESTION, updated.getType());
        assertEquals(Set.of("jpa"), tagNames(updated));
        assertEquals(Set.of("java"), tagNames(postService.findPostById(second.getId())));
        assertNotNull(updated.getUpdatedAt());
        assertEquals(2, tagRepository.count());

        assertEquals(PostOperationResult.SUCCESS, postService.deletePost(first.getId(), author));
        postRepository.flush();
        assertEquals(2, tagRepository.count());
    }

    @Test
    void returnsStablePagedPostsWithTotalsAndOptionalNormalizedTagFilter() throws Exception {
        Post first = register("첫 글", "첫 내용", PostType.STUDY_LOG, List.of("java"));
        Post second = register("둘째 글", "둘째 내용", PostType.QUESTION, List.of("spring"));
        Post third = register("셋째 글", "셋째 내용", PostType.STUDY_LOG, List.of("JAVA"));

        mvc.perform(get("/posts").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].id").value(third.getId()))
                .andExpect(jsonPath("$.content[1].id").value(second.getId()))
                .andExpect(jsonPath("$.content[1].type").value("QUESTION"))
                .andExpect(jsonPath("$.content[0].createdAt").exists())
                .andExpect(jsonPath("$.content[0].updatedAt").exists());

        mvc.perform(get("/posts")
                        .param("tag", " JAVA ")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(third.getId()))
                .andExpect(jsonPath("$.content[0].tags[0]").value("java"));

        mvc.perform(get("/posts").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(first.getId()));
    }

    @Test
    void rejectsInvalidPageAndSize() throws Exception {
        mvc.perform(get("/posts").param("page", "-1"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/posts").param("size", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/posts").param("size", "51"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAndUpdateApiAcceptOptionalTypeAndTagsWhileKeepingOldDefaults() throws Exception {
        MockHttpSession session = registerAndLogin("api-author@example.com");

        mvc.perform(post("/posts")
                        .session(session)
                        .with(csrf())
                        .param("title", "기본 타입 글")
                        .param("content", "기존 요청 형식"))
                .andExpect(status().isCreated());

        Long defaultPostId = postRepository.findAll().get(0).getId();
        Post defaultPost = postRepository.findById(defaultPostId).orElseThrow();
        assertEquals(PostType.STUDY_LOG, defaultPost.getType());
        assertTrue(defaultPost.getTags().isEmpty());

        mvc.perform(post("/posts")
                        .session(session)
                        .with(csrf())
                        .param("title", "질문 글")
                        .param("content", "태그가 있는 요청")
                        .param("type", "QUESTION")
                        .param("tags", " Java ", "SPRING", "java"))
                .andExpect(status().isCreated());

        Long questionId = postRepository.findAll().stream()
                .filter(post -> post.getTitle().equals("질문 글"))
                .findFirst()
                .orElseThrow()
                .getId();
        Post question = postRepository.findById(questionId).orElseThrow();
        assertEquals(PostType.QUESTION, question.getType());
        assertEquals(Set.of("java", "spring"), tagNames(question));

        mvc.perform(patch("/posts/{id}", question.getId())
                        .session(session)
                        .with(csrf())
                        .param("title", "태그 유지 질문")
                        .param("content", "선택 매개변수를 생략한 수정"))
                .andExpect(status().isOk());

        Post tagsPreserved = postRepository.findById(question.getId()).orElseThrow();
        assertEquals(PostType.QUESTION, tagsPreserved.getType());
        assertEquals(Set.of("java", "spring"), tagNames(tagsPreserved));

        mvc.perform(patch("/posts/{id}", question.getId())
                        .session(session)
                        .with(csrf())
                        .param("title", "수정된 질문")
                        .param("content", "수정된 내용")
                        .param("tags", "jpa"))
                .andExpect(status().isOk());

        Post updated = postRepository.findById(question.getId()).orElseThrow();
        assertEquals(PostType.QUESTION, updated.getType());
        assertEquals(Set.of("jpa"), tagNames(updated));

        mvc.perform(patch("/posts/{id}", question.getId())
                        .session(session)
                        .with(csrf())
                        .param("title", "태그 없는 질문")
                        .param("content", "빈 태그 입력으로 관계 제거")
                        .param("tags", ""))
                .andExpect(status().isOk());

        Post tagsCleared = postService.findPostById(question.getId());
        assertEquals(PostType.QUESTION, tagsCleared.getType());
        assertTrue(tagsCleared.getTags().isEmpty());

        mvc.perform(post("/posts")
                        .session(session)
                        .with(csrf())
                        .param("title", "태그 초과")
                        .param("content", "저장되면 안 됨")
                        .param("tags", "one", "two", "three", "four"))
                .andExpect(status().isBadRequest());
        assertEquals(2, postRepository.count());
    }

    @Test
    void createApiAcceptsTheCommaSeparatedTagValueSentByTheBrowserUi() throws Exception {
        MockHttpSession session = registerAndLogin("browser-tags@example.com");

        mvc.perform(post("/posts")
                        .session(session)
                        .with(csrf())
                        .param("title", "브라우저 태그")
                        .param("content", "URLSearchParams 요청")
                        .param("type", "QUESTION")
                        .param("tags", "Java, Spring, java"))
                .andExpect(status().isCreated());

        Long savedId = postRepository.findAll().stream()
                .filter(candidate -> candidate.getTitle().equals("브라우저 태그"))
                .findFirst()
                .orElseThrow()
                .getId();
        Post saved = postService.findPostById(savedId);
        assertEquals(Set.of("java", "spring"), tagNames(saved));
    }

    private Post register(String title, String content, PostType type, List<String> tags) {
        Post post = new Post(title, content, author, type);
        assertTrue(postService.addPost(post, tags));
        return post;
    }

    private Set<String> tagNames(Post post) {
        return post.getTags().stream().map(tag -> tag.getName()).collect(java.util.stream.Collectors.toSet());
    }

    private MockHttpSession registerAndLogin(String email) throws Exception {
        mvc.perform(post("/auth/register")
                        .with(csrf())
                        .param("email", email)
                        .param("password", "correct-password"))
                .andExpect(status().isCreated());

        MvcResult loginResult = mvc.perform(post("/auth/login")
                        .with(csrf())
                        .param("email", email)
                        .param("password", "correct-password"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) loginResult.getRequest().getSession(false);
    }
}

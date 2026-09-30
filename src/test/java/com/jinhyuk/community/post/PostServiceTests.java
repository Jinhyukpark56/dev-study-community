package com.jinhyuk.community.post;

import com.jinhyuk.community.user.User;
import com.jinhyuk.community.user.UserRepository;
import com.jinhyuk.community.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PostServiceTests {

    @Autowired
    private PostService service;

    @Autowired
    private PostRepository repository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    private User author;

    @BeforeEach
    void clearPosts() {
        repository.deleteAll();
        userRepository.deleteAll();
        assertTrue(userService.register("post-author@example.com", "author-password"));
        author = userRepository.findByEmail("post-author@example.com").orElseThrow();
    }

    @Test
    void registersPostAndGeneratesId() {
        Post post = new Post("Java", "JPA 공부", author);

        assertTrue(service.addPost(post));
        assertNotNull(post.getId());
        assertEquals(1, repository.count());
    }

    @Test
    void findsPostById() {
        Post saved = register("List", "ArrayList 공부");

        Post found = service.findPostById(saved.getId());

        assertNotNull(found);
        assertEquals(saved.getId(), found.getId());
        assertEquals("List", found.getTitle());
        assertEquals("ArrayList 공부", found.getContent());
    }

    @Test
    void returnsNullForMissingId() {
        assertNull(service.findPostById(999L));
        assertNull(service.findPostById(null));
    }

    @Test
    void listsSavedPosts() {
        register("첫 글", "첫 내용");
        register("둘째 글", "둘째 내용");

        assertEquals(2, service.findAllPosts().size());
    }

    @Test
    void updatesTitleAndContentWithoutChangingId() {
        Post saved = register("이전 제목", "이전 내용");

        assertEquals(
                PostOperationResult.SUCCESS,
                service.updatePost(saved.getId(), author, "새 제목", "새 내용"));

        Post found = service.findPostById(saved.getId());
        assertNotNull(found);
        assertEquals(saved.getId(), found.getId());
        assertEquals("새 제목", found.getTitle());
        assertEquals("새 내용", found.getContent());
    }

    @Test
    void deletesOnlyTargetPost() {
        Post first = register("같은 제목", "첫 글");
        Post second = register("같은 제목", "둘째 글");

        assertEquals(PostOperationResult.SUCCESS, service.deletePost(first.getId(), author));
        assertNull(service.findPostById(first.getId()));
        assertEquals("둘째 글", service.findPostById(second.getId()).getContent());
        assertEquals(PostOperationResult.NOT_FOUND, service.deletePost(first.getId(), author));
    }

    @Test
    void rejectsUpdateAndDeleteForMissingId() {
        assertEquals(
                PostOperationResult.NOT_FOUND,
                service.updatePost(999L, author, "제목", "내용"));
        assertEquals(PostOperationResult.NOT_FOUND, service.deletePost(999L, author));
        assertEquals(
                PostOperationResult.NOT_FOUND,
                service.updatePost(null, author, "제목", "내용"));
        assertEquals(PostOperationResult.NOT_FOUND, service.deletePost(null, author));
    }

    @Test
    void rejectsNullPost() {
        assertFalse(service.addPost(null));
        assertEquals(0, repository.count());
    }

    @Test
    void rejectsPostWithoutAuthor() {
        assertFalse(service.addPost(new Post("제목", "내용", null)));
        assertEquals(0, repository.count());
    }

    @Test
    void rejectsInvalidTitlesOnRegistration() {
        String[] invalidTitles = {null, "", " \t\n", "가".repeat(101)};

        for (String title : invalidTitles) {
            assertFalse(service.addPost(new Post(title, "내용", author)));
        }

        assertEquals(0, repository.count());
    }

    @Test
    void rejectsInvalidContentsOnRegistration() {
        String[] invalidContents = {null, "", " \t\n", "나".repeat(1001)};

        for (String content : invalidContents) {
            assertFalse(service.addPost(new Post("제목", content, author)));
        }

        assertEquals(0, repository.count());
    }

    @Test
    void acceptsExactLengthLimitsOnRegistrationAndUpdate() {
        Post post = new Post("가".repeat(100), "나".repeat(1000), author);

        assertTrue(service.addPost(post));
        assertEquals(
                PostOperationResult.SUCCESS,
                service.updatePost(
                        post.getId(),
                        author,
                        "다".repeat(100),
                        "라".repeat(1000)));
        assertEquals("다".repeat(100), service.findPostById(post.getId()).getTitle());
        assertEquals("라".repeat(1000), service.findPostById(post.getId()).getContent());
    }

    @Test
    void rejectsInvalidUpdatesWithoutPartialChanges() {
        Post saved = register("원래 제목", "원래 내용");
        String[] invalidTitles = {null, "", " \t\n", "가".repeat(101)};
        String[] invalidContents = {null, "", " \t\n", "나".repeat(1001)};

        for (String title : invalidTitles) {
            assertEquals(
                    PostOperationResult.INVALID_INPUT,
                    service.updatePost(saved.getId(), author, title, "바뀌면 안 되는 내용"));
            assertOriginalValuesRemain(saved.getId());
        }
        for (String content : invalidContents) {
            assertEquals(
                    PostOperationResult.INVALID_INPUT,
                    service.updatePost(saved.getId(), author, "바뀌면 안 되는 제목", content));
            assertOriginalValuesRemain(saved.getId());
        }
    }

    @Test
    void generatedIdsAreDifferent() {
        Post first = register("첫 글", "첫 내용");
        Post second = register("둘째 글", "둘째 내용");

        assertNotEquals(first.getId(), second.getId());
    }

    @Test
    void rejectsAlreadyPersistedPostAsNewRegistration() {
        Post saved = register("제목", "내용");

        assertFalse(service.addPost(saved));
        assertEquals(1, repository.count());
    }

    @Test
    void serviceReadsPostStoredThroughRepository() {
        Post saved = repository.save(new Post("제목", "내용", author));

        assertNotNull(service.findPostById(saved.getId()));
    }

    private Post register(String title, String content) {
        Post post = new Post(title, content, author);
        assertTrue(service.addPost(post));
        return post;
    }

    private void assertOriginalValuesRemain(Long id) {
        Post found = service.findPostById(id);
        assertNotNull(found);
        assertEquals("원래 제목", found.getTitle());
        assertEquals("원래 내용", found.getContent());
    }
}

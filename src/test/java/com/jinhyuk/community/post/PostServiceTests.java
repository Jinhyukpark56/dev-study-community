package com.jinhyuk.community.post;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PostServiceTests {

    @Test
    void addsAndFindsPostById() {
        PostService service = new PostService();
        assertTrue(service.addPost(new Post(1, "Java", "OOP 공부")));
        assertTrue(service.addPost(new Post(2, "List", "ArrayList 공부")));

        Post found = service.findPostById(2);
        assertNotNull(found);
        assertEquals(2, found.getId());
        assertEquals("List", found.getTitle());
        assertEquals("ArrayList 공부", found.getContent());
    }

    @Test
    void returnsNullForMissingId() {
        PostService service = new PostService();
        assertNull(service.findPostById(1));
        service.addPost(new Post(1, "Java", "내용"));
        assertNull(service.findPostById(99));
    }

    @Test
    void updatesTitleAndContentWithoutChangingId() {
        PostService service = new PostService();
        service.addPost(new Post(1, "이전 제목", "이전 내용"));
        assertTrue(service.updatePost(1, "새 제목", "새 내용"));
        Post found = service.findPostById(1);
        assertEquals(1, found.getId());
        assertEquals("새 제목", found.getTitle());
        assertEquals("새 내용", found.getContent());
    }

    @Test
    void deletesOnlyTargetPost() {
        PostService service = new PostService();
        service.addPost(new Post(1, "같은 제목", "첫 글"));
        service.addPost(new Post(2, "같은 제목", "둘째 글"));
        assertTrue(service.deletePost(1));
        assertNull(service.findPostById(1));
        assertEquals("둘째 글", service.findPostById(2).getContent());
        assertFalse(service.deletePost(1));
    }

    @Test
    void rejectsUpdateAndDeleteForMissingId() {
        PostService service = new PostService();
        assertFalse(service.updatePost(99, "제목", "내용"));
        assertFalse(service.deletePost(99));
    }

    @Test
    void rejectsNullPost() {
        PostService service = new PostService();
        assertFalse(service.addPost(null));
    }

    @Test
    void rejectsInvalidTitlesOnRegistration() {
        String[] invalidTitles = {null, "", " \t\n", "가".repeat(101)};
        for (String title : invalidTitles) {
            PostService service = new PostService();
            assertFalse(service.addPost(new Post(1, title, "내용")));
            assertNull(service.findPostById(1));
        }
    }

    @Test
    void rejectsInvalidContentsOnRegistration() {
        String[] invalidContents = {null, "", " \t\n", "나".repeat(1001)};
        for (String content : invalidContents) {
            PostService service = new PostService();
            assertFalse(service.addPost(new Post(1, "제목", content)));
            assertNull(service.findPostById(1));
        }
    }

    @Test
    void acceptsExactLengthLimitsOnRegistrationAndUpdate() {
        PostService service = new PostService();
        String title = "가".repeat(100);
        String content = "나".repeat(1000);
        assertTrue(service.addPost(new Post(1, title, content)));
        assertTrue(service.updatePost(1, "다".repeat(100), "라".repeat(1000)));
        assertEquals("다".repeat(100), service.findPostById(1).getTitle());
        assertEquals("라".repeat(1000), service.findPostById(1).getContent());
    }

    @Test
    void rejectsInvalidUpdatesWithoutPartialChanges() {
        PostService service = new PostService();
        service.addPost(new Post(1, "원래 제목", "원래 내용"));
        String[] invalidTitles = {null, "", " \t\n", "가".repeat(101)};
        String[] invalidContents = {null, "", " \t\n", "나".repeat(1001)};
        for (String title : invalidTitles) {
            assertFalse(service.updatePost(1, title, "바뀌면 안 되는 내용"));
            assertEquals("원래 제목", service.findPostById(1).getTitle());
            assertEquals("원래 내용", service.findPostById(1).getContent());
        }
        for (String content : invalidContents) {
            assertFalse(service.updatePost(1, "바뀌면 안 되는 제목", content));
            assertEquals("원래 제목", service.findPostById(1).getTitle());
            assertEquals("원래 내용", service.findPostById(1).getContent());
        }
    }

    @Test
    void rejectsDuplicateIdWithoutReplacingExistingPost() {
        PostService service = new PostService();
        service.addPost(new Post(1, "첫 글", "원래 내용"));
        assertFalse(service.addPost(new Post(1, "중복 글", "다른 내용")));
        assertEquals("첫 글", service.findPostById(1).getTitle());
        assertTrue(service.deletePost(1));
        assertNull(service.findPostById(1));
    }

    @Test
    void externalChangesCannotBypassServiceValidation() {
        PostService service = new PostService();
        Post input = new Post(1, "제목", "내용");
        service.addPost(input);
        input.setTitle(null);
        input.setContent(" ");
        Post found = service.findPostById(1);
        found.setTitle("");
        found.setContent(null);
        assertEquals("제목", service.findPostById(1).getTitle());
        assertEquals("내용", service.findPostById(1).getContent());
    }

    @Test
    void serviceInstancesHaveSeparateStorage() {
        PostService first = new PostService();
        PostService second = new PostService();
        first.addPost(new Post(1, "제목", "내용"));
        assertNull(second.findPostById(1));
    }
}

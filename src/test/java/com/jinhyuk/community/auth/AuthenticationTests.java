package com.jinhyuk.community.auth;

import com.jinhyuk.community.user.User;
import com.jinhyuk.community.user.UserRepository;
import com.jinhyuk.community.post.PostRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationTests {

    private static final String EMAIL = "student@example.com";
    private static final String PASSWORD = "correct-password";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void clearUsers() {
        postRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void registersUserAndStoresOnlyEncodedPassword() throws Exception {
        register(" Student@Example.com ", PASSWORD)
                .andExpect(status().isCreated());

        User saved = userRepository.findByEmail(EMAIL).orElseThrow();
        assertNotNull(saved.getId());
        assertEquals(EMAIL, saved.getEmail());
        assertNotEquals(PASSWORD, saved.getPassword());
        assertTrue(passwordEncoder.matches(PASSWORD, saved.getPassword()));
    }

    @Test
    void rejectsDuplicateEmail() throws Exception {
        register(EMAIL, PASSWORD)
                .andExpect(status().isCreated());

        register("STUDENT@example.com", "another-password")
                .andExpect(status().isBadRequest());

        assertEquals(1, userRepository.count());
    }

    @Test
    void rejectsInvalidEmailAndPassword() throws Exception {
        register("invalid-email", PASSWORD)
                .andExpect(status().isBadRequest());
        register(EMAIL, "too-short")
                .andExpect(status().isBadRequest());
        register(EMAIL, "a".repeat(73))
                .andExpect(status().isBadRequest());

        assertEquals(0, userRepository.count());
    }

    @Test
    void refreshesCsrfTokenAfterLoginBeforeLogout() throws Exception {
        MvcResult csrfResult = mvc.perform(get("/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.parameterName").value("_csrf"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        MockHttpSession session = (MockHttpSession) csrfResult.getRequest().getSession(false);
        String token = JsonPath.read(csrfResult.getResponse().getContentAsString(), "$.token");
        assertNotNull(session);

        mvc.perform(post("/auth/register")
                        .session(session)
                        .header("X-CSRF-TOKEN", token)
                        .param("email", EMAIL)
                        .param("password", PASSWORD))
                .andExpect(status().isCreated());

        MvcResult loginResult = mvc.perform(post("/auth/login")
                        .session(session)
                        .header("X-CSRF-TOKEN", token)
                        .param("email", EMAIL)
                        .param("password", PASSWORD))
                .andExpect(status().isOk())
                .andReturn();
        session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mvc.perform(post("/auth/logout")
                        .session(session)
                        .header("X-CSRF-TOKEN", token))
                .andExpect(status().isForbidden());

        MvcResult refreshedCsrf = mvc.perform(get("/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andReturn();
        String refreshedToken = JsonPath.read(
                refreshedCsrf.getResponse().getContentAsString(),
                "$.token");

        mvc.perform(post("/auth/logout")
                        .session(session)
                        .header("X-CSRF-TOKEN", refreshedToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void logsInAndKeepsAuthenticationInSession() throws Exception {
        register(EMAIL, PASSWORD).andExpect(status().isCreated());

        MvcResult loginResult = login(EMAIL, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(authenticated().withUsername(EMAIL))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertNotNull(session);

        mvc.perform(get("/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(authenticated().withUsername(EMAIL))
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    void rejectsWrongPasswordAndUnknownEmailWithSameStatus() throws Exception {
        register(EMAIL, PASSWORD).andExpect(status().isCreated());

        login(EMAIL, "wrong-password-value")
                .andExpect(status().isUnauthorized())
                .andExpect(unauthenticated());
        login("missing@example.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(unauthenticated());
    }

    @Test
    void logoutInvalidatesAuthenticatedSession() throws Exception {
        register(EMAIL, PASSWORD).andExpect(status().isCreated());
        MvcResult loginResult = login(EMAIL, PASSWORD)
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertNotNull(session);

        mvc.perform(post("/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(unauthenticated());

        assertTrue(session.isInvalid());
        mvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(unauthenticated());
    }

    @Test
    void appliesPublicReadAndAuthenticatedWritePolicy() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk());
        mvc.perform(get("/posts/1"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/posts").with(csrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requiresCsrfForAuthenticationChanges() throws Exception {
        mvc.perform(post("/auth/register")
                        .param("email", EMAIL)
                        .param("password", PASSWORD))
                .andExpect(status().isForbidden());

        assertFalse(userRepository.existsByEmail(EMAIL));
    }

    private ResultActions register(String email, String password) throws Exception {
        return mvc.perform(post("/auth/register")
                .with(csrf())
                .param("email", email)
                .param("password", password));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/auth/login")
                .with(csrf())
                .param("email", email)
                .param("password", password));
    }
}

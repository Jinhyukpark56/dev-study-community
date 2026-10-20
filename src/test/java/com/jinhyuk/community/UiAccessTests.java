package com.jinhyuk.community;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UiAccessTests {

    @Autowired
    private MockMvc mvc;

    @Test
    void publicUiAndAssetsAreAvailableWithoutLogin() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(unauthenticated())
                .andExpect(forwardedUrl("index.html"));
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(unauthenticated())
                .andExpect(content().contentTypeCompatibleWith("text/html"));
        mvc.perform(get("/app.js"))
                .andExpect(status().isOk())
                .andExpect(unauthenticated())
                .andExpect(content().contentTypeCompatibleWith("text/javascript"));
        mvc.perform(get("/styles.css"))
                .andExpect(status().isOk())
                .andExpect(unauthenticated())
                .andExpect(content().contentTypeCompatibleWith("text/css"));
    }
}

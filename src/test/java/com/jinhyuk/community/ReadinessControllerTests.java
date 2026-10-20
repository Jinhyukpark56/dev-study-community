package com.jinhyuk.community;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReadinessControllerTests {

    @Autowired
    private MockMvc mvc;

    @Test
    void readinessChecksDatabaseConnection() throws Exception {
        mvc.perform(get("/ready"))
                .andExpect(status().isOk())
                .andExpect(content().string("ready"));
    }
}

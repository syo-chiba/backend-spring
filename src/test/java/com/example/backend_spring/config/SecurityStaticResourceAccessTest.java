package com.example.backend_spring.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityStaticResourceAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void appCss_shouldBeAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/app.css"))
                .andExpect(status().isOk());
    }

    @Test
    void flows_shouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/flows"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", Matchers.containsString("/login")));
    }
}

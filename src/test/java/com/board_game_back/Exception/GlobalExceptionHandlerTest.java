package com.board_game_back.Exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 클라이언트가 잘못 보낸 요청은 500이 아니라 400 + INVALID_REQUEST. 그동안 깨진 JSON·타입 불일치가 500이었다. */
class GlobalExceptionHandlerTest {

    record Body(double rating) {}

    @RestController
    static class Dummy {
        @PutMapping("/body")
        String body(@RequestBody Body body) { return "ok"; }

        @GetMapping("/path/{id}")
        String path(@PathVariable Long id) { return "ok"; }

        @GetMapping("/param")
        String param(@RequestParam Long roomId) { return "ok"; }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new Dummy())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void 깨진_JSON은_400() throws Exception {
        mvc.perform(put("/body").contentType(MediaType.APPLICATION_JSON).content("{\"rating\":"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void 숫자_자리에_문자나_null이면_400() throws Exception {
        mvc.perform(put("/body").contentType(MediaType.APPLICATION_JSON).content("{\"rating\":\"abc\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(put("/body").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void 경로_id가_숫자가_아니면_400() throws Exception {
        mvc.perform(get("/path/abc"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void 필수_파라미터가_없으면_400() throws Exception {
        mvc.perform(get("/param"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}

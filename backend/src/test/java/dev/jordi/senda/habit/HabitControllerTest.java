package dev.jordi.senda.habit;

import dev.jordi.senda.apitoken.ApiTokenService;
import dev.jordi.senda.common.GlobalExceptionHandler;
import dev.jordi.senda.common.JwtAuthFilter;
import dev.jordi.senda.common.JwtService;
import dev.jordi.senda.common.SecurityConfig;
import dev.jordi.senda.common.UnprocessableEntityException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({HabitController.class, HabitEntryController.class})
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtService.class, GlobalExceptionHandler.class})
class HabitControllerTest {

    private static final Long USER_ID = 1L;

    @MockitoBean private ApiTokenService apiTokenService;
    @MockitoBean private HabitService habitService;
    @MockitoBean private HabitEntryService habitEntryService;
    @MockitoBean private HabitQueryService habitQueryService;

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;

    private String bearer;

    @BeforeEach
    void setUp() {
        bearer = "Bearer " + jwtService.generateToken(USER_ID);
    }

    @Test
    void requestWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/habits/today")).andExpect(status().isUnauthorized());
    }

    @Test
    void todayReturnsTheList() throws Exception {
        when(habitQueryService.today(USER_ID)).thenReturn(List.of(
                new TodayHabitResponse(1L, "Agua", "💧", HabitType.COUNTER,
                        new BigDecimal("8"), "vasos", new BigDecimal("5"), false, 4)));

        mockMvc.perform(get("/api/habits/today").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Agua"))
                .andExpect(jsonPath("$[0].currentStreak").value(4));
    }

    @Test
    void postWithBlankNameReturns400() throws Exception {
        mockMvc.perform(post("/api/habits")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \",\"type\":\"CHECK\",\"scheduleType\":\"WEEKDAYS\",\"weekdays\":\"1111111\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void aDateOutsideTheWindowReturns422() throws Exception {
        when(habitEntryService.record(eq(USER_ID), eq(1L), any(LocalDate.class), any()))
                .thenThrow(new UnprocessableEntityException("Date outside the editable window of 7 days"));

        mockMvc.perform(put("/api/habits/1/entries/2026-07-01")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":null}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }
}

package dev.jordi.senda.habit;

import com.jayway.jsonpath.JsonPath;
import dev.jordi.senda.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class HabitIntegrationTest {

    @Autowired private MockMvc mockMvc;
    private String tokenA, tokenB;

    @BeforeEach
    void setUp() throws Exception {
        tokenA = register("hab-int-a@example.com");
        tokenB = register("hab-int-b@example.com");
    }

    private String register(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\",\"name\":\"X\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private long createDailyCheck(String token, String name) throws Exception {
        String body = mockMvc.perform(post("/api/habits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"type\":\"CHECK\","
                                + "\"scheduleType\":\"WEEKDAYS\",\"weekdays\":\"1111111\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void anotherUsersHabitIsNotFound() throws Exception {
        long habitId = createDailyCheck(tokenA, "Leer");

        mockMvc.perform(get("/api/habits/" + habitId + "/history?from=2026-08-01&to=2026-08-03")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/habits/" + habitId + "/entries/" + LocalDate.now())
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void recordingTodayShowsUpInTheTodayView() throws Exception {
        long habitId = createDailyCheck(tokenA, "Meditar");

        mockMvc.perform(put("/api/habits/" + habitId + "/entries/" + LocalDate.now())
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.done").value(true));

        mockMvc.perform(get("/api/habits/today").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].done").value(true))
                .andExpect(jsonPath("$[0].currentStreak").value(1));
    }

    @Test
    void aDateOutsideTheWindowIsRejected() throws Exception {
        long habitId = createDailyCheck(tokenA, "Leer");

        mockMvc.perform(put("/api/habits/" + habitId + "/entries/" + LocalDate.now().minusDays(30))
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void deletingTheHabitRemovesItFromTheList() throws Exception {
        long habitId = createDailyCheck(tokenA, "Temporal");

        mockMvc.perform(delete("/api/habits/" + habitId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/habits").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}

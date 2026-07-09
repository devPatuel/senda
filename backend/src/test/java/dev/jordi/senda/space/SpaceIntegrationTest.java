package dev.jordi.senda.space;

import com.jayway.jsonpath.JsonPath;
import dev.jordi.senda.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasSize;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class SpaceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private String register(String email, String name) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "password123", "name": "%s"}
                                """.formatted(email, name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private long createSpace(String token, String name) throws Exception {
        String body = mockMvc.perform(post("/api/spaces")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void registerAndCreateSpace_returns201WithActiveStatus() throws Exception {
        String tokenA = register("a@example.com", "User A");

        mockMvc.perform(post("/api/spaces")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Pareja"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Pareja"))
                .andExpect(jsonPath("$.myStatus").value("ACTIVE"));

        mockMvc.perform(get("/api/spaces").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].myStatus").value("ACTIVE"));
    }

    @Test
    void inviteAcceptFlow_makesBothActiveMembers() throws Exception {
        String tokenA = register("a@example.com", "User A");
        String tokenB = register("her@example.com", "User B");
        long spaceId = createSpace(tokenA, "Pareja");

        mockMvc.perform(post("/api/spaces/" + spaceId + "/members")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "her@example.com"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.email").value("her@example.com"));

        mockMvc.perform(get("/api/spaces").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].myStatus").value("PENDING"));

        mockMvc.perform(post("/api/spaces/" + spaceId + "/accept")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/spaces/" + spaceId + "/members")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.status == 'ACTIVE')]", hasSize(2)));
    }

    @Test
    void nonMemberGetsMembers_returns404() throws Exception {
        String tokenA = register("a@example.com", "User A");
        register("c@example.com", "User C");
        String tokenC = register("c2@example.com", "User C2");
        long spaceId = createSpace(tokenA, "Pareja");

        mockMvc.perform(get("/api/spaces/" + spaceId + "/members")
                        .header("Authorization", "Bearer " + tokenC))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/spaces/" + spaceId + "/members")
                        .header("Authorization", "Bearer " + tokenC)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "c@example.com"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void inviteUnknownEmail_returns404() throws Exception {
        String tokenA = register("a@example.com", "User A");
        long spaceId = createSpace(tokenA, "Pareja");

        mockMvc.perform(post("/api/spaces/" + spaceId + "/members")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "ghost@example.com"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void inviteExistingMember_returns409() throws Exception {
        String tokenA = register("a@example.com", "User A");
        String tokenB = register("her@example.com", "User B");
        long spaceId = createSpace(tokenA, "Pareja");

        mockMvc.perform(post("/api/spaces/" + spaceId + "/members")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "her@example.com"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/spaces/" + spaceId + "/accept")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/spaces/" + spaceId + "/members")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "her@example.com"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void declineRemovesPendingInvite() throws Exception {
        String tokenA = register("a@example.com", "User A");
        String tokenB = register("her@example.com", "User B");
        long spaceId = createSpace(tokenA, "Pareja");

        mockMvc.perform(post("/api/spaces/" + spaceId + "/members")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "her@example.com"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/spaces/" + spaceId + "/decline")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/spaces").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void leaveRemovesActiveMembership() throws Exception {
        String tokenA = register("a@example.com", "User A");
        String tokenB = register("her@example.com", "User B");
        long spaceId = createSpace(tokenA, "Pareja");

        mockMvc.perform(post("/api/spaces/" + spaceId + "/members")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "her@example.com"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/spaces/" + spaceId + "/accept")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/spaces/" + spaceId + "/members/me")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/spaces/" + spaceId + "/members")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}

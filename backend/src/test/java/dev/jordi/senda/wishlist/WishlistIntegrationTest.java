package dev.jordi.senda.wishlist;

import java.util.List;
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
class WishlistIntegrationTest {

    @Autowired private MockMvc mockMvc;
    private String tokenA, tokenB;

    @BeforeEach
    void setUp() throws Exception {
        tokenA = register("wl-a@example.com");
        tokenB = register("wl-b@example.com");
    }

    private String register(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\",\"name\":\"X\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private long createItem(String token, String name, String price) throws Exception {
        String body = mockMvc.perform(post("/api/wishlist")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"price\":" + price + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void rejectsLinksThatAreNotWebAddresses() throws Exception {
        for (String field : List.of("productUrl", "imageUrl")) {
            mockMvc.perform(post("/api/wishlist")
                            .header("Authorization", "Bearer " + tokenA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"NAS\",\"" + field + "\":\"javascript:alert(1)\"}"))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/api/wishlist")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"NAS\",\"productUrl\":\"https://example.com/nas\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void getReturnsItemsAndTotal() throws Exception {
        createItem(tokenA, "NAS", "500.00");
        createItem(tokenA, "Silla", "150.00");
        mockMvc.perform(get("/api/wishlist").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.total").value(650.00));
    }

    @Test
    void crossTenant_cannotReadEditOrDeleteForeignItem() throws Exception {
        long idA = createItem(tokenA, "Privado", "10.00");

        mockMvc.perform(get("/api/wishlist").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
        mockMvc.perform(put("/api/wishlist/" + idA)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"hack\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/wishlist/" + idA)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    void requestWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/wishlist")).andExpect(status().isUnauthorized());
    }
}

package dev.jordi.senda.product;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ProductIntegrationTest {

    @Autowired private MockMvc mockMvc;

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void registerUsers() throws Exception {
        tokenA = register("product-usera@example.com", "User A");
        tokenB = register("product-userb@example.com", "User B");
    }

    private String register(String email, String name) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","name":"%s"}
                                """.formatted(email, name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private long createProduct(String token, String body) throws Exception {
        String res = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$.id")).longValue();
    }

    private void addPrice(String token, long productId, String body) throws Exception {
        mockMvc.perform(post("/api/products/" + productId + "/prices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void currentPriceIsLatestPerSupermarketCheapestFirst() throws Exception {
        long id = createProduct(tokenA, """
                {"name":"Leche","unitType":"WEIGHT","amount":1.0,"unit":"L"}
                """);
        addPrice(tokenA, id, "{\"price\":1.30,\"supermarket\":\"Mercadona\"}");
        addPrice(tokenA, id, "{\"price\":1.10,\"supermarket\":\"Lidl\"}");
        addPrice(tokenA, id, "{\"price\":1.25,\"supermarket\":\"Mercadona\"}"); // newer -> current

        // History has all 3 entries
        mockMvc.perform(get("/api/products/" + id + "/prices")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        // Comparison: Lidl 1.10 first, Mercadona current 1.25 second
        mockMvc.perform(get("/api/products").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currentPrices.length()").value(2))
                .andExpect(jsonPath("$[0].currentPrices[0].supermarket").value("Lidl"))
                .andExpect(jsonPath("$[0].currentPrices[0].price").value(1.10))
                .andExpect(jsonPath("$[0].currentPrices[1].supermarket").value("Mercadona"))
                .andExpect(jsonPath("$[0].currentPrices[1].price").value(1.25));
    }

    @Test
    void userBCannotSeeOrTouchUserAProduct() throws Exception {
        long idA = createProduct(tokenA, """
                {"name":"Pan de A","unitType":"QUANTITY","amount":1.0,"unit":"ud"}
                """);

        // B's catalog is empty
        mockMvc.perform(get("/api/products").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // B cannot read A's product detail -> 404
        mockMvc.perform(get("/api/products/" + idA).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // B cannot register a price on A's product -> 404
        mockMvc.perform(post("/api/products/" + idA + "/prices")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":1.00,\"supermarket\":\"Lidl\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // B cannot read A's price history -> 404
        mockMvc.perform(get("/api/products/" + idA + "/prices")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // B cannot delete A's product -> 404
        mockMvc.perform(delete("/api/products/" + idA).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // A's product remains intact
        mockMvc.perform(get("/api/products").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Pan de A"));
    }
}

package dev.jordi.senda.shoppinglist;

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
class ShoppingListIntegrationTest {

    @Autowired private MockMvc mockMvc;
    private String tokenA, tokenB;

    @BeforeEach
    void setUp() throws Exception {
        tokenA = register("sl-a@example.com");
        tokenB = register("sl-b@example.com");
    }

    private String register(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\",\"name\":\"X\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private long createProduct(String token, String name) throws Exception {
        String body = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"unitType\":\"QUANTITY\",\"amount\":1.0,\"unit\":\"ud\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private void addPrice(String token, long productId, String price) throws Exception {
        mockMvc.perform(post("/api/products/" + productId + "/prices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":" + price + ",\"supermarket\":\"Lidl\"}"))
                .andExpect(status().isCreated());
    }

    private long addToList(String token, long productId, int qty) throws Exception {
        String body = mockMvc.perform(post("/api/shopping-list")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"quantity\":" + qty + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void estimatedTotalSumsLatestPricesTimesQuantity() throws Exception {
        long milk = createProduct(tokenA, "Leche");
        addPrice(tokenA, milk, "1.10");
        long bread = createProduct(tokenA, "Pan");
        addPrice(tokenA, bread, "0.90");
        addToList(tokenA, milk, 2);   // 2.20
        addToList(tokenA, bread, 1);  // 0.90

        mockMvc.perform(get("/api/shopping-list").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.estimatedTotal").value(3.10));
    }

    @Test
    void addingForeignProductReturns404() throws Exception {
        long productB = createProduct(tokenB, "De B");
        mockMvc.perform(post("/api/shopping-list")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productB + "}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicateProductReturns400() throws Exception {
        long milk = createProduct(tokenA, "Leche");
        addToList(tokenA, milk, 1);
        mockMvc.perform(post("/api/shopping-list")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + milk + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crossTenantCannotUpdateOrDeleteForeignListItem() throws Exception {
        long milk = createProduct(tokenA, "Leche");
        long itemA = addToList(tokenA, milk, 1);

        mockMvc.perform(put("/api/shopping-list/" + itemA)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"checked\":true}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/shopping-list/" + itemA)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    void clearCheckedRemovesOnlyCheckedItems() throws Exception {
        long milk = createProduct(tokenA, "Leche");
        long bread = createProduct(tokenA, "Pan");
        long itemMilk = addToList(tokenA, milk, 1);
        addToList(tokenA, bread, 1);

        mockMvc.perform(put("/api/shopping-list/" + itemMilk)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"checked\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/shopping-list/checked").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/shopping-list").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productName").value("Pan"));
    }
}

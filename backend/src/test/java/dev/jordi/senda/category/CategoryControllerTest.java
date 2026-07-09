package dev.jordi.senda.category;

import dev.jordi.senda.common.ConflictException;
import dev.jordi.senda.common.GlobalExceptionHandler;
import dev.jordi.senda.common.JwtAuthFilter;
import dev.jordi.senda.common.JwtService;
import dev.jordi.senda.common.NotFoundException;
import dev.jordi.senda.common.SecurityConfig;
import dev.jordi.senda.common.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CategoryController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtService.class, GlobalExceptionHandler.class})
class CategoryControllerTest {

    private static final Long USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private CategoryService categoryService;

    private String bearer;

    @BeforeEach
    void setUp() {
        bearer = "Bearer " + jwtService.generateToken(USER_ID);
    }

    @Test
    void listReturns200WithCategories() throws Exception {
        when(categoryService.list(USER_ID, null, null, false)).thenReturn(List.of(
                new CategoryResponse(1L, "Comida", TransactionType.EXPENSE, "#EF4444", true)));

        mockMvc.perform(get("/api/categories").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Comida"))
                .andExpect(jsonPath("$[0].type").value("EXPENSE"))
                .andExpect(jsonPath("$[0].color").value("#EF4444"))
                .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    void listPassesTypeAndIncludeInactiveToService() throws Exception {
        when(categoryService.list(USER_ID, null, TransactionType.INCOME, true)).thenReturn(List.of());

        mockMvc.perform(get("/api/categories")
                        .param("type", "INCOME")
                        .param("includeInactive", "true")
                        .header("Authorization", bearer))
                .andExpect(status().isOk());

        verify(categoryService).list(USER_ID, null, TransactionType.INCOME, true);
    }

    @Test
    void listWithInvalidTypeReturns400() throws Exception {
        mockMvc.perform(get("/api/categories")
                        .param("type", "BOGUS")
                        .header("Authorization", bearer))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReturns201() throws Exception {
        when(categoryService.create(eq(USER_ID), any(CategoryRequest.class))).thenReturn(
                new CategoryResponse(10L, "Gimnasio", TransactionType.EXPENSE, "#FF8800", true));

        mockMvc.perform(post("/api/categories")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Gimnasio", "type": "EXPENSE", "color": "#FF8800"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.name").value("Gimnasio"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void createWithInvalidBodyReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "color": "red"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.type").exists())
                .andExpect(jsonPath("$.fieldErrors.color").exists());
    }

    @Test
    void createWithNameOver100CharsReturns400() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "type": "EXPENSE", "color": "#FF8800"}
                                """.formatted("a".repeat(101))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void createDuplicateReturns409() throws Exception {
        when(categoryService.create(eq(USER_ID), any(CategoryRequest.class)))
                .thenThrow(new ConflictException("Category already exists"));

        mockMvc.perform(post("/api/categories")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Comida", "type": "EXPENSE", "color": "#FF8800"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void updateReturns200() throws Exception {
        when(categoryService.update(eq(USER_ID), eq(5L), any(CategoryUpdateRequest.class))).thenReturn(
                new CategoryResponse(5L, "Alimentación", TransactionType.EXPENSE, "#00FF00", false));

        mockMvc.perform(put("/api/categories/5")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Alimentación", "color": "#00FF00", "active": false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alimentación"))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void updateCategoryOfAnotherUserReturns404() throws Exception {
        when(categoryService.update(eq(USER_ID), eq(99L), any(CategoryUpdateRequest.class)))
                .thenThrow(new NotFoundException("Category not found"));

        mockMvc.perform(put("/api/categories/99")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Comida", "color": "#EF4444"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/categories/5").header("Authorization", bearer))
                .andExpect(status().isNoContent());

        verify(categoryService).delete(USER_ID, 5L);
    }

    @Test
    void deleteCategoryOfAnotherUserReturns404() throws Exception {
        doThrow(new NotFoundException("Category not found"))
                .when(categoryService).delete(USER_ID, 99L);

        mockMvc.perform(delete("/api/categories/99").header("Authorization", bearer))
                .andExpect(status().isNotFound());
    }

    @Test
    void requestWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}

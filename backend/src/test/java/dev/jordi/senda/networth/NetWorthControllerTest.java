package dev.jordi.senda.networth;

import dev.jordi.senda.common.GlobalExceptionHandler;
import dev.jordi.senda.common.JwtAuthFilter;
import dev.jordi.senda.apitoken.ApiTokenService;
import dev.jordi.senda.common.JwtService;
import dev.jordi.senda.common.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NetWorthController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtService.class, GlobalExceptionHandler.class})
class NetWorthControllerTest {

    @MockitoBean
    private ApiTokenService apiTokenService;

    private static final Long USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private NetWorthService netWorthService;

    private String bearer;

    @BeforeEach
    void setUp() {
        bearer = "Bearer " + jwtService.generateToken(USER_ID);
    }

    private static final NetWorthResponse SAMPLE = new NetWorthResponse(
            new BigDecimal("3000.00"),
            new BigDecimal("40500.00"),
            new BigDecimal("40000.00"),
            new BigDecimal("500.00"),
            new BigDecimal("150.00"),
            new BigDecimal("700.00"),
            new BigDecimal("42950.00"),
            new BigDecimal("0.00"));

    @Test
    void requestWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/networth"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void getWithValidTokenReturns200WithShape() throws Exception {
        when(netWorthService.calculate(any())).thenReturn(SAMPLE);

        mockMvc.perform(get("/api/networth").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liquid").value(3000.00))
                .andExpect(jsonPath("$.investments").value(40500.00))
                .andExpect(jsonPath("$.investmentsHoldings").value(40000.00))
                .andExpect(jsonPath("$.investmentsNfts").value(500.00))
                .andExpect(jsonPath("$.debtsInFavor").value(150.00))
                .andExpect(jsonPath("$.debtsAgainst").value(700.00))
                .andExpect(jsonPath("$.net").value(42950.00));
    }
}

package dev.jordi.senda.investment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CoinbasePriceProviderTest {

    private static final String URL = "https://api.coinbase.com/v2/exchange-rates?currency=EUR";

    private MockRestServiceServer server;
    private CoinbasePriceProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new CoinbasePriceProvider(builder);
    }

    @Test
    void convertsRatesToEurPricesAndIgnoresMissingOrUnusableRates() {
        server.expect(requestTo(URL)).andRespond(withSuccess("""
                {"data":{"currency":"EUR","rates":{"BTC":"0.00001","CRO":"16","XRP":"0","ADA":"abc"}}}
                """, MediaType.APPLICATION_JSON));

        Map<String, BigDecimal> prices = provider.pricesInEur(Set.of("BTC", "CRO", "XRP", "ADA", "SOL"));

        // Coinbase quotes units per 1 EUR, so the EUR price is the inverse
        assertThat(prices).containsOnlyKeys("BTC", "CRO");
        assertThat(prices.get("BTC")).isEqualByComparingTo("100000");
        assertThat(prices.get("CRO")).isEqualByComparingTo("0.0625");
    }

    @Test
    void httpErrorDegradesToEmpty() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThat(provider.pricesInEur(Set.of("BTC"))).isEmpty();
    }

    @Test
    void emptyRequestDoesNotCallTheNetwork() {
        assertThat(provider.pricesInEur(Set.of())).isEmpty();
        server.verify();
    }
}

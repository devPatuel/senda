package dev.jordi.senda.investment;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CoinGeckoPriceProviderTest {

    @Test
    void mapsTickersToCoinGeckoIds() {
        assertThat(CoinGeckoPriceProvider.coinGeckoId("BTC")).isEqualTo("bitcoin");
        // CoinGecko's id for Cronos is not its ticker: "cro" does not exist upstream
        assertThat(CoinGeckoPriceProvider.coinGeckoId("cro")).isEqualTo("crypto-com-chain");
    }

    @Test
    void unmappedTickerFallsBackToLowercasedSymbol() {
        assertThat(CoinGeckoPriceProvider.coinGeckoId("PEPE")).isEqualTo("pepe");
    }

    @Test
    void emptyRequestReturnsEmptyWithoutCallingTheNetwork() {
        assertThat(new CoinGeckoPriceProvider().pricesInEur(Set.of())).isEmpty();
    }
}

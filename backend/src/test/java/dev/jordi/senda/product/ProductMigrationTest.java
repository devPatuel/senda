package dev.jordi.senda.product;

import dev.jordi.senda.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ProductMigrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void productTablesExist() {
        Integer products = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'products'",
                Integer.class);
        Integer prices = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'price_entries'",
                Integer.class);
        assertThat(products).isEqualTo(1);
        assertThat(prices).isEqualTo(1);
    }
}

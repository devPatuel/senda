package dev.jordi.senda.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class UnprocessableEntityExceptionTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsToUnprocessableEntityWithTheMessage() {
        ResponseEntity<ErrorResponse> response =
                handler.handleUnprocessable(new UnprocessableEntityException("Date out of window"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().status()).isEqualTo(422);
        assertThat(response.getBody().message()).isEqualTo("Date out of window");
    }
}

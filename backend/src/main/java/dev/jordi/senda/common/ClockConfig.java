package dev.jordi.senda.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ClockConfig {

    /** Madrid, not the JVM default: the container runs on UTC and a 00:30 entry
     *  would otherwise land on the previous day. */
    public static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

    @Bean
    public Clock clock() {
        return Clock.system(ZONE);
    }
}

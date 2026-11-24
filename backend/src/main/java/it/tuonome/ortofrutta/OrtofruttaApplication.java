package it.tuonome.ortofrutta;

import it.tuonome.ortofrutta.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
@EnableScheduling
public class OrtofruttaApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrtofruttaApplication.class, args);
    }
}

package it.tuonome.ortofrutta;

import it.tuonome.ortofrutta.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class OrtofruttaApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrtofruttaApplication.class, args);
    }
}

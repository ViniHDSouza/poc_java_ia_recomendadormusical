package br.com.vini.recomendadormusical;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PocJavaIaRecomendadorMusicalApplication {

    public static void main(String[] args) {
        SpringApplication.run(PocJavaIaRecomendadorMusicalApplication.class, args);
    }
}

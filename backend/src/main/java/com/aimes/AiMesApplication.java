package com.aimes;

import com.aimes.config.DotEnvLoader;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

@SpringBootApplication
@MapperScan("com.aimes.mapper")
public class AiMesApplication {

    public static void main(String[] args) {
        DotEnvLoader.load();
        SpringApplication.run(AiMesApplication.class, args);
    }

    @Bean
    CommandLineRunner startupNotice(
            @Value("${server.port:8080}") String port,
            @Value("${coze.bot-id:}") String cozeBotId
    ) {
        return args -> {
            System.out.println();
            System.out.println("========================================");
            System.out.println("AI-MES backend started");
            System.out.println("URL:  http://localhost:" + port);
            System.out.println("Docs: http://localhost:" + port + "/doc.html");
            System.out.println("Coze: " + (StringUtils.hasText(cozeBotId) ? "configured" : "demo mode (missing bot id)"));
            System.out.println("========================================");
            System.out.println();
        };
    }
}

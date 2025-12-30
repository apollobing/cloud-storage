package com.example.cloudstorage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

@SpringBootApplication
public class CloudStorageApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(CloudStorageApplication.class, args);
        Environment env = context.getEnvironment();
        
        String serverPort = env.getProperty("server.port", "8080");

        System.out.println("\n" + "=".repeat(80));
        System.out.println("🚀  Cloud Storage Application Started Successfully!");
        System.out.println("=".repeat(80));
        System.out.println();
        System.out.println("📌  Frontend URLs:");
        System.out.println("   • Nginx Frontend:     http://localhost");
        System.out.println("   • Static Frontend:    http://localhost:" + serverPort);
        System.out.println();
        System.out.println("📚  Backend & Documentation:");
        System.out.println("   • Backend API:        http://localhost:" + serverPort + "/api/");
        System.out.println("   • Swagger UI:         http://localhost:" + serverPort + "/swagger-ui/index.html");
        System.out.println();
        System.out.println("💡  Tips:");
        System.out.println("   • Run infrastructure: docker-compose up -d");
        System.out.println("   • Backend ready:      Already running on port " + serverPort);
        System.out.println();
        System.out.println("=".repeat(80) + "\n");
    }

}

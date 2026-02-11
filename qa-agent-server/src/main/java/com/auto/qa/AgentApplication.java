package com.auto.qa;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AgentApplication {
    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.load();
        String geminiApiKey = dotenv.get("GEMINI_API_KEY");
        if (geminiApiKey != null) {
            System.setProperty("GEMINI_API_KEY", geminiApiKey);
        }

        String geminiProjectId = dotenv.get("GEMINI_PROJECT_ID");
        if (geminiProjectId != null) {
            System.setProperty("GEMINI_PROJECT_ID", geminiProjectId);
        }

        String geminiModel = dotenv.get("GEMINI_MODEL");
        if (geminiModel != null) {
            System.setProperty("GEMINI_MODEL", geminiModel);
        }

        String geminiTemperature = dotenv.get("GEMINI_TEMPERATURE");
        if (geminiTemperature != null) {
            System.setProperty("GEMINI_TEMPERATURE", geminiTemperature);
        }

        String ollamaBaseUrl = dotenv.get("OLLAMA_BASE_URL");
        if (ollamaBaseUrl != null) {
            System.setProperty("OLLAMA_BASE_URL", ollamaBaseUrl);
        }

        String ollamaModel = dotenv.get("OLLAMA_MODEL");
        if (ollamaModel != null) {
            System.setProperty("OLLAMA_MODEL", ollamaModel);
        }

        String ollamaTemperature = dotenv.get("OLLAMA_TEMPERATURE");
        if (ollamaTemperature != null) {
            System.setProperty("OLLAMA_TEMPERATURE", ollamaTemperature);
        }

        SpringApplication.run(AgentApplication.class, args);
    }
}

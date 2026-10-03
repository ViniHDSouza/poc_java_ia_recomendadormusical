package br.com.vini.recomendadormusical.exception;

public class MissingOpenRouterApiKeyException extends RuntimeException {
    public MissingOpenRouterApiKeyException() {
        super("A variável OPENROUTER_API_KEY não foi configurada. Consulte o README.md e o arquivo .env.example.");
    }
}

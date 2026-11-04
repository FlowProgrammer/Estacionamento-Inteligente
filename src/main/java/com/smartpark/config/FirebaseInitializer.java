package com.smartpark.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;

/**
 * Inicializa o Firebase Admin SDK usando EXCLUSIVAMENTE variáveis de ambiente.
 *
 * Variáveis:
 *  - FIREBASE_PROJECT_ID               (obrigatória)
 *  - FIREBASE_SERVICE_ACCOUNT_JSON     (opcional; conteúdo do JSON, tem prioridade)
 *  - GOOGLE_APPLICATION_CREDENTIALS    (opcional; caminho do JSON fora do repositório)
 *
 * Nada aqui é logado em relação a credenciais.
 */
public final class FirebaseInitializer {

    private FirebaseInitializer() {}

    /** Inicializa o app uma única vez (idempotente) e devolve o Firestore. */
    public static synchronized Firestore initFirestore() {
        if (FirebaseApp.getApps().isEmpty()) {
            try {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(loadCredentials())
                        .setProjectId(requireEnv("FIREBASE_PROJECT_ID"))
                        .build();
                FirebaseApp.initializeApp(options);
            } catch (IOException e) {
                // Mensagem genérica: não vazar caminho/conteúdo da credencial.
                throw new IllegalStateException(
                        "Falha ao carregar credenciais do Firebase. Verifique as variáveis de ambiente.");
            }
        }
        return FirestoreClient.getFirestore();
    }

    private static GoogleCredentials loadCredentials() throws IOException {
        String json = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON");
        if (json != null && !json.isBlank()) {
            try (InputStream in = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))) {
                return GoogleCredentials.fromStream(in);
            }
        }

        String path = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (path != null && !path.isBlank()) {
            try (InputStream in = new FileInputStream(path)) {
                return GoogleCredentials.fromStream(in);
            }
        }

        throw new IllegalStateException(
                "Defina FIREBASE_SERVICE_ACCOUNT_JSON ou GOOGLE_APPLICATION_CREDENTIALS.");
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Variável de ambiente obrigatória ausente: " + name);
        }
        return value;
    }
}

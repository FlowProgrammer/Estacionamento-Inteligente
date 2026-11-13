package br.com.smartpark.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
public class FirebaseConfig {

    @Value("${FIREBASE_CREDENTIALS_JSON:}")
    private String credentialsJson;

    @Value("${FIREBASE_CREDENTIALS_BASE64:}")
    private String credentialsBase64;

    @Value("${FIREBASE_PROJECT_ID:}")
    private String projectId;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        FirebaseOptions.Builder builder = FirebaseOptions.builder()
                .setCredentials(loadCredentials());
        if (!projectId.isBlank()) {
            builder.setProjectId(projectId);
        }
        return FirebaseApp.initializeApp(builder.build());
    }

    @Bean
    public Firestore firestore(FirebaseApp app) {
        return FirestoreClient.getFirestore(app);
    }

    private GoogleCredentials loadCredentials() throws IOException {
        byte[] json = null;
        if (!credentialsBase64.isBlank()) {
            json = Base64.getDecoder().decode(credentialsBase64.trim());
        } else if (!credentialsJson.isBlank()) {
            json = credentialsJson.getBytes(StandardCharsets.UTF_8);
        }

        if (json != null) {
            return GoogleCredentials.fromStream(new ByteArrayInputStream(json));
        }
        // Fallback: GOOGLE_APPLICATION_CREDENTIALS (caminho) ou identidade de serviço
        try {
            return GoogleCredentials.getApplicationDefault();
        } catch (IOException e) {
            // Mensagem clara, sem vazar conteúdo de credenciais
            throw new IllegalStateException(
                "Credenciais do Firebase não encontradas. Defina FIREBASE_CREDENTIALS_JSON "
                + "ou FIREBASE_CREDENTIALS_BASE64.", e);
        }
    }
}
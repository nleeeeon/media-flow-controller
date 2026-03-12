// src/main/java/service/auth/IdTokenParser.java
package service.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class IdTokenParser {

    public JsonObject parsePayload(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new IllegalArgumentException("id_token is missing");
        }

        String[] parts = idToken.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("id_token format is invalid");
        }

        byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
        String payloadJson = new String(decoded, StandardCharsets.UTF_8);

        return JsonParser.parseString(payloadJson).getAsJsonObject();
    }

    public String extractSub(String idToken) {
        JsonObject payload = parsePayload(idToken);
        if (!payload.has("sub")) {
            throw new IllegalArgumentException("sub is missing in id_token payload");
        }
        return payload.get("sub").getAsString();
    }
}
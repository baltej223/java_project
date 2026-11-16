package com.example.crobat.http;

import com.fasterxml.jackson.databind.JsonNode;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** HS256 sign/verify. No third-party auth library. */
public final class Jwt {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final String HEADER =
            ENCODER.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}"
                    .getBytes(StandardCharsets.UTF_8));

    private Jwt() {
    }

    public static String sign(String secret, String tenant, long ttlSeconds) {
        try {
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("sub", tenant);
            claims.put("exp", Instant.now().getEpochSecond() + ttlSeconds);
            String payload = ENCODER.encodeToString(
                    Json.MAPPER.writeValueAsBytes(claims));
            String signingInput = HEADER + "." + payload;
            byte[] signature = hmac(secret, signingInput);
            return signingInput + "." + ENCODER.encodeToString(signature);
        } catch (Exception ex) {
            throw new IllegalStateException("cannot sign token", ex);
        }
    }

    /** Returns the tenant, or null for any invalid token. */
    public static String tenantFrom(String authorization, String secret) {
        try {
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                return null;
            }
            String token = authorization.substring("Bearer ".length());
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) {
                return null;
            }

            JsonNode header = Json.MAPPER.readTree(DECODER.decode(parts[0]));
            if (header == null || !"HS256".equals(header.path("alg").asText())) {
                return null;
            }

            byte[] expected = hmac(secret, parts[0] + "." + parts[1]);
            byte[] actual = DECODER.decode(parts[2]);
            if (!MessageDigest.isEqual(expected, actual)) {
                return null;
            }

            JsonNode claims = Json.MAPPER.readTree(DECODER.decode(parts[1]));
            if (claims == null) {
                return null;
            }
            String tenant = claims.path("sub").asText("");
            long exp = claims.path("exp").asLong(0);
            if (tenant.isEmpty() || tenant.length() > 64) {
                return null;
            }
            if (exp <= Instant.now().getEpochSecond()) {
                return null;
            }
            return tenant;
        } catch (Exception ex) {
            return null;
        }
    }

    private static byte[] hmac(String secret, String message) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
    }
}

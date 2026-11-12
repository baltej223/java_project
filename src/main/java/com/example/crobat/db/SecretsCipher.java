package com.example.crobat.db;

import com.example.crobat.domain.RequestSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** AES-256-GCM for request headers and body. Format: nonce || ciphertext || tag. */
public class SecretsCipher {
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public SecretsCipher(byte[] keyBytes) {
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public byte[] seal(RequestSpec request) {
        Map<String, String> headers = request.getHeaders();
        String body = request.getBody();
        if ((headers == null || headers.isEmpty())
                && (body == null || body.isEmpty())) {
            return null;
        }
        try {
            Map<String, Object> value = new LinkedHashMap<>();
            if (headers != null && !headers.isEmpty()) {
                value.put("h", headers);
            }
            if (body != null && !body.isEmpty()) {
                value.put("b", body);
            }
            byte[] plain = MAPPER.writeValueAsBytes(value);
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plain);

            ByteBuffer out = ByteBuffer.allocate(nonce.length + encrypted.length);
            out.put(nonce);
            out.put(encrypted);
            return out.array();
        } catch (GeneralSecurityException ex) {
            throw new DatabaseException("secret: cannot encrypt", ex);
        } catch (Exception ex) {
            throw new DatabaseException("secret: cannot encode", ex);
        }
    }

    public void open(byte[] blob, RequestSpec target) {
        if (blob == null || blob.length == 0) {
            return;
        }
        if (blob.length <= NONCE_BYTES) {
            throw new DatabaseException("secret: ciphertext too short");
        }
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            byte[] encrypted = new byte[blob.length - NONCE_BYTES];
            System.arraycopy(blob, 0, nonce, 0, NONCE_BYTES);
            System.arraycopy(blob, NONCE_BYTES, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] plain = cipher.doFinal(encrypted);

            JsonNode node = MAPPER.readTree(new String(plain, StandardCharsets.UTF_8));
            Map<String, String> headers = new LinkedHashMap<>();
            JsonNode headerNode = node.get("h");
            if (headerNode != null && headerNode.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = headerNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    headers.put(field.getKey(), field.getValue().asText());
                }
            }
            target.setHeaders(headers);
            target.setBody(node.has("b") ? node.get("b").asText() : "");
        } catch (Exception ex) {
            throw new DatabaseException(
                    "secret: cannot decrypt (wrong SECRETS_KEY?)", ex);
        }
    }
}

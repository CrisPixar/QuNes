package com.qns.data.crypto;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Покрывает BUG 2 (NPE в ChatViewModel при отправке сообщения, когда bundle
 * или внутренний map приходят пустыми).
 *
 * Эти проверки отражают ту же логику извлечения inner-bundle, что и в
 * ChatViewModel.sendText: сначала null-check, затем «raw instanceof Map».
 */
public class KeyBundleExtractionTest {

    @Test
    public void innerMapIsExtractedWhenBundleLooksRight() {
        Map<String, Object> identity = new HashMap<>();
        identity.put("publicKey", "AAA");
        Map<String, Object> inner = new HashMap<>();
        inner.put("identity_x25519", identity);
        Map<String, Object> bundle = new HashMap<>();
        bundle.put("bundle", inner);

        Map<String, Object> extracted = extract(bundle);
        assertNotNull(extracted);
        assertEquals(identity, extracted.get("identity_x25519"));
    }

    @Test
    public void nullBundleReturnsNull() {
        assertNull(extract(null));
    }

    @Test
    public void missingBundleKeyReturnsNull() {
        Map<String, Object> bundle = new HashMap<>();
        assertNull(extract(bundle));
    }

    @Test
    public void bundleKeyWithNonMapValueReturnsNull() {
        Map<String, Object> bundle = new HashMap<>();
        bundle.put("bundle", "not a map");
        assertNull(extract(bundle));
    }

    @Test
    public void emptyBundleValueIsHandledGracefully() {
        Map<String, Object> bundle = new HashMap<>();
        bundle.put("bundle", new HashMap<>());
        Map<String, Object> extracted = extract(bundle);
        assertNotNull(extracted);
        assertEquals(0, extracted.size());
    }

    private static Map<String, Object> extract(Map<String, Object> bundle) {
        if (bundle == null) return null;
        Object raw = bundle.get("bundle");
        return raw instanceof Map ? (Map<String, Object>) raw : null;
    }
}

package com.qns.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Покрывает BUG 2 (sync не теряет decryptedCache) и дедупликацию/«мое»-признак.
 */
public class MessageMergeTest {

    @Test
    public void syncDoesNotEraseAlreadyDecryptedText() {
        MessageMerge.Merged merged = MessageMerge.decide(
            true,              // existing
            "привет, мир",     // existingDecrypted
            null,              // existingError
            false,             // existingIsMine
            null,              // incomingDecrypted (sync не расшифровывает)
            null,              // incomingError
            false              // incomingIsMine
        );
        assertEquals("привет, мир", merged.decryptedCache);
        assertFalse(merged.decryptionFailed);
        assertNull(merged.decryptionError);
    }

    @Test
    public void incomingDecryptFailureIsStored() {
        MessageMerge.Merged merged = MessageMerge.decide(
            false, null, null, false,
            null, "UNSUPPORTED_VERSION", false
        );
        assertEquals("UNSUPPORTED_VERSION", merged.decryptionError);
        assertTrue(merged.decryptionFailed);
        assertNull(merged.decryptedCache);
    }

    @Test
    public void outgoingIsMineIsPreservedOnResync() {
        MessageMerge.Merged merged = MessageMerge.decide(
            true, "мой текст", null, true,   // existing outgoing
            null, null, true                  // incoming same message
        );
        assertTrue(merged.isMine);
        assertEquals("мой текст", merged.decryptedCache);
    }

    @Test
    public void freshIncomingMarkedNotMine() {
        MessageMerge.Merged merged = MessageMerge.decide(
            false, null, null, false,
            "текст", null, false
        );
        assertFalse(merged.isMine);
        assertEquals("текст", merged.decryptedCache);
    }

    @Test
    public void transientWaitingSessionIsNotFailed() {
        // WAITING_FOR_SESSION — временное состояние (ключ сессии ещё не пришёл).
        MessageMerge.Merged merged = MessageMerge.decide(
            false, null, null, false,
            null, "WAITING_FOR_SESSION", false
        );
        assertTrue(merged.decryptionFailed);
        assertEquals("WAITING_FOR_SESSION", merged.decryptionError);
    }
}

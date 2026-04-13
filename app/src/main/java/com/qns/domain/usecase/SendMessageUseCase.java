package com.qns.domain.usecase;

import com.qns.data.remote.WebSocketClient;
import javax.inject.Inject;

public class SendMessageUseCase {
    private final WebSocketClient ws;
    @Inject public SendMessageUseCase(WebSocketClient ws) { this.ws = ws; }

    public void execute(String chatId, String encryptedPayload, String ratchetHeader, String signature) {
        if (chatId == null || encryptedPayload == null) return;
        ws.sendMessage(chatId, encryptedPayload, ratchetHeader, signature);
    }
}

import { z } from 'zod';

export const QPacketSchema = z.object({
  type: z.enum(['text', 'image', 'audio', 'call_signal']),
  payload: z.string(), // Encrypted payload
  signature: z.string(), // Dilithium-3 signature
  iv: z.string(), // Nonce
  senderId: z.string(),
  chatId: z.string(),
  timestamp: z.number()
});

export type QPacket = z.infer<typeof QPacketSchema>;
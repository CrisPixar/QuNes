import type { RtpCodecCapability } from 'mediasoup/node/lib/types';

export const config = {
  listenIp: process.env.MEDIASOUP_LISTEN_IP || '0.0.0.0',
  announcedIp: process.env.MEDIASOUP_ANNOUNCED_IP || '127.0.0.1',
  rtcMinPort: 40000,
  rtcMaxPort: 49999,
  mediasoup: {
    worker: {
      rtcMinPort: 40000,
      rtcMaxPort: 49999,
      logLevel: 'debug' as any,
      logTags: [
        'info', 'ice', 'dtls', 'rtp', 'srtp', 'rtcp'
      ] as any
    },
    router: {
      mediaCodecs: [
        {
          kind: 'audio',
          mimeType: 'audio/opus',
          clockRate: 48000,
          channels: 2
        },
        {
          kind: 'video',
          mimeType: 'video/VP9',
          clockRate: 90000,
          parameters: {
            'profile-id': 2,
            'pqc-mode': 'enforced'
          }
        }
      ] as RtpCodecCapability[]
    }
  }
};
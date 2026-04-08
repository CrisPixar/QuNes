FROM node:20

# System dependencies for mediasoup binary builds
RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY package*.json ./
RUN npm install

COPY . .
RUN npm run build

# Dynamic RTP ports range required by mediasoup workers
EXPOSE 40000-49999/udp
# Signaling Port
EXPOSE 4000

CMD ["npm", "run", "start"]
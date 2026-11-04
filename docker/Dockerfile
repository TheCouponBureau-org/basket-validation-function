FROM node:20-alpine

ENV NODE_ENV=production
WORKDIR /app

# Install the Express server and the current SDK from this repository.
COPY js ./js
COPY docker/package.json docker/package-lock.json ./docker/
WORKDIR /app/docker
RUN npm ci --omit=dev && npm cache clean --force

COPY docker/src ./src

USER node
EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
    CMD node -e "fetch('http://127.0.0.1:3000/healthz').then(r=>{if(!r.ok)process.exit(1)}).catch(()=>process.exit(1))"

CMD ["node", "src/server.js"]

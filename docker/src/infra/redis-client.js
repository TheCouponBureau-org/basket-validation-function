"use strict";

const { createClient } = require("redis");

async function createRedisClient(redisUrl) {
    const client = createClient({ url: redisUrl });
    client.on("error", (error) => {
        console.error("[Redis]", error.message);
    });
    await client.connect();
    return client;
}

module.exports = { createRedisClient };

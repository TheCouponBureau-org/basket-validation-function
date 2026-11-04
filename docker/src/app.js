"use strict";

const express = require("express");
const { errorHandler } = require("./http/errors");
const { createRoutes } = require("./routes");

function createApp(dependencies) {
    const app = express();
    app.disable("x-powered-by");
    app.use(express.json({ limit: "1mb" }));

    app.get("/", (_request, response) => {
        response.json({ status: "ok", service: "tcb-basket-validator" });
    });
    app.get(
        "/healthz",
        dependencies.asyncHealthCheck || ((_request, response) => response.json({ status: "ok" }))
    );
    app.use(createRoutes(dependencies));
    app.use(errorHandler);
    return app;
}

module.exports = { createApp };

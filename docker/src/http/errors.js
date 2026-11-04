"use strict";

class ClientInputError extends Error {
    constructor(message, details) {
        super(message);
        this.name = "ClientInputError";
        this.statusCode = 400;
        this.details = details;
    }
}

function asyncRoute(handler) {
    return (request, response, next) => {
        Promise.resolve(handler(request, response, next)).catch(next);
    };
}

function errorHandler(error, request, response, _next) {
    if (error instanceof SyntaxError && error.status === 400 && "body" in error) {
        return response.status(400).json({
            status: "error",
            message: "Invalid JSON payload",
        });
    }

    const statusCode = error.statusCode || (error instanceof TypeError ? 400 : 500);
    console.error("[Request Error]", {
        method: request.method,
        path: request.originalUrl,
        message: error.message,
    });

    const body = { status: "error", message: error.message };
    if (error.code) body.code = error.code;
    if (error.details) body.details = error.details;
    return response.status(statusCode).json(body);
}

module.exports = { ClientInputError, asyncRoute, errorHandler };

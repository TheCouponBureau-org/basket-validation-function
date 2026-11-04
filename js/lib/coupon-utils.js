"use strict";

const {
    CONSUMER_SERIALIZED_PREFIX,
    MIN_BASE_GS1_LENGTH,
    MIN_SERIALIZED_GS1_LENGTH,
    SERIAL_COMPONENT_LENGTH,
} = require("./constants");

function chunk(values, size) {
    const chunks = [];
    for (let index = 0; index < values.length; index += size) {
        chunks.push(values.slice(index, index + size));
    }
    return chunks;
}

function stripLastFourDigits(gs1) {
    return typeof gs1 === "string" && gs1.length > 4 ? gs1.slice(0, -4) : null;
}

function findMatchingRedeemedCoupon(requestedGs1, newlyRedeemed) {
    if (!requestedGs1 || !Array.isArray(newlyRedeemed)) {
        return undefined;
    }

    const exact = newlyRedeemed.find((coupon) => coupon && coupon.gs1 === requestedGs1);
    if (exact) {
        return exact;
    }

    const requestedBaseGs1 = stripLastFourDigits(requestedGs1);
    return newlyRedeemed.find(
        (coupon) => coupon && requestedBaseGs1 && coupon.master_offer_file === requestedBaseGs1
    );
}

function resolveBaseGs1Length(serializedGs1, startIndex = 0) {
    const extensionDigit = serializedGs1[startIndex + 5];
    if (!/\d/.test(extensionDigit || "")) {
        return -1;
    }
    return MIN_BASE_GS1_LENGTH + Number(extensionDigit);
}

function tryParseConsumerSerializedGs1s(scannedGs1) {
    if (
        typeof scannedGs1 !== "string" ||
        scannedGs1.length < MIN_SERIALIZED_GS1_LENGTH ||
        !/^\d+$/.test(scannedGs1)
    ) {
        return [];
    }

    const parsed = [];
    for (let index = 0; index < scannedGs1.length; ) {
        if (!scannedGs1.startsWith(CONSUMER_SERIALIZED_PREFIX, index)) {
            return [];
        }

        const baseGs1Length = resolveBaseGs1Length(scannedGs1, index);
        const serializedLength = baseGs1Length + SERIAL_COMPONENT_LENGTH;
        if (
            baseGs1Length < MIN_BASE_GS1_LENGTH ||
            serializedLength < MIN_SERIALIZED_GS1_LENGTH ||
            index + serializedLength > scannedGs1.length
        ) {
            return [];
        }

        const gs1 = scannedGs1.slice(index, index + serializedLength);
        parsed.push({
            gs1,
            base_gs1: gs1.slice(0, baseGs1Length),
            validated: false,
        });
        index += serializedLength;
    }

    return parsed;
}

function expandRequestedGs1s(gs1) {
    const parsed = tryParseConsumerSerializedGs1s(gs1);
    return parsed.length > 1 ? parsed.map((item) => item.gs1) : [gs1];
}

module.exports = {
    chunk,
    expandRequestedGs1s,
    findMatchingRedeemedCoupon,
    resolveBaseGs1Length,
    stripLastFourDigits,
    tryParseConsumerSerializedGs1s,
};

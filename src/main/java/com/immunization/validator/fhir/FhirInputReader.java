package com.immunization.validator.fhir;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.immunization.validator.error.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reads FHIR R4 input text into a flat list of resources. Accepts:
 * <ul>
 *   <li>a Bundle of any type (its entries, with their fullUrl),</li>
 *   <li>a JSON array of resources,</li>
 *   <li>NDJSON: one resource per line, two or more non-blank lines,</li>
 *   <li>one lone resource.</li>
 * </ul>
 * Anything else fails with {@link FhirMappingException} and {@link ErrorCode#MALFORMED_REQUEST}.
 *
 * Plain Java on purpose: no Spring, so it can be tested without a context.
 * Mirrors load_input in the kit's fhir_mapper_reference.py.
 */
public class FhirInputReader {

    public static final String NOT_JSON = "NOT_JSON";
    public static final String NOT_AN_OBJECT = "NOT_AN_OBJECT";
    public static final String NOT_A_RESOURCE = "NOT_A_RESOURCE";

    /**
     * Trailing tokens must fail: by default Jackson would read the first line of an NDJSON body,
     * ignore the rest and silently lose every resource after it.
     */
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    /** One resource, and the Bundle entry's fullUrl when it had one (urn:uuid references match it). */
    @Getter
    @AllArgsConstructor
    public static class Entry {
        private final JsonNode resource;
        private final String fullUrl;
    }

    public List<Entry> read(String text) {
        String stripped = text == null ? "" : text.strip();
        // A byte-order mark is not part of the JSON; some exports start with one.
        if (stripped.startsWith("﻿")) {
            stripped = stripped.substring(1).strip();
        }

        JsonNode root = parse(stripped);
        if (root == null) {
            root = parseNdjson(stripped);
        }

        if (root.isObject() && "Bundle".equals(textValue(root.get("resourceType")))) {
            return bundleEntries(root.get("entry"));
        }
        if (root.isObject()) {
            List<Entry> lone = new ArrayList<>();
            lone.add(new Entry(root, null));
            return lone;
        }
        if (root.isArray()) {
            List<Entry> entries = new ArrayList<>();
            for (JsonNode item : root) {
                if (!item.isObject()) {
                    throw malformed(NOT_A_RESOURCE);
                }
                entries.add(new Entry(item, null));
            }
            return entries;
        }
        throw malformed(NOT_AN_OBJECT);
    }

    /** NDJSON needs two or more non-blank lines. A single unreadable line is just unreadable JSON. */
    private JsonNode parseNdjson(String text) {
        List<String> lines = text.lines().filter(line -> !line.isBlank()).collect(Collectors.toList());
        if (lines.size() < 2) {
            throw malformed(NOT_JSON);
        }
        var array = JsonNodeFactory.instance.arrayNode();
        for (String line : lines) {
            JsonNode node = parse(line.strip());
            if (node == null) {
                throw malformed(NOT_JSON);
            }
            array.add(node);
        }
        return array;
    }

    private List<Entry> bundleEntries(JsonNode entryList) {
        List<Entry> entries = new ArrayList<>();
        if (entryList == null || entryList.isNull()) {
            return entries;
        }
        if (!entryList.isArray()) {
            throw malformed(NOT_A_RESOURCE);
        }
        for (JsonNode entry : entryList) {
            if (entry == null || entry.isNull()) {
                entries.add(new Entry(emptyResource(), null));
                continue;
            }
            if (!entry.isObject()) {
                throw malformed(NOT_A_RESOURCE);
            }
            JsonNode resource = entry.get("resource");
            if (resource == null || resource.isNull()) {
                resource = emptyResource();
            } else if (!resource.isObject()) {
                throw malformed(NOT_A_RESOURCE);
            }
            String fullUrl = textValue(entry.get("fullUrl"));
            entries.add(new Entry(resource, fullUrl == null || fullUrl.isEmpty() ? null : fullUrl));
        }
        return entries;
    }

    /** The parsed value, or null when the text isn't exactly one JSON value. */
    private static JsonNode parse(String text) {
        try {
            JsonNode node = JSON.readTree(text);
            return node == null || node.isMissingNode() ? null : node;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static ObjectNode emptyResource() {
        return JsonNodeFactory.instance.objectNode();
    }

    private static String textValue(JsonNode node) {
        return node != null && node.isTextual() ? node.textValue() : null;
    }

    private static FhirMappingException malformed(String reason) {
        return new FhirMappingException(ErrorCode.MALFORMED_REQUEST, null, reason);
    }
}

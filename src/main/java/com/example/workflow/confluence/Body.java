package com.example.workflow.confluence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Тело страницы Confluence (body.storage при expand=body.storage).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Body {

    private BodyStorage storage;

    public BodyStorage getStorage() {
        return storage;
    }

    public void setStorage(BodyStorage storage) {
        this.storage = storage;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BodyStorage {
        private String value;
        private String representation;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getRepresentation() {
            return representation;
        }

        public void setRepresentation(String representation) {
            this.representation = representation;
        }
    }
}

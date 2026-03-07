package com.example.workflow.confluence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Links {

    private String self;
    private String webui;

    public String getSelf() {
        return self;
    }

    public void setSelf(String self) {
        this.self = self;
    }

    public String getWebui() {
        return webui;
    }

    public void setWebui(String webui) {
        this.webui = webui;
    }
}

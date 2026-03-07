package com.example.workflow.confluence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO ответа Confluence REST API GET /rest/api/content/{id}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PageContent {

    private String id;
    private String type;
    private String title;
    private Body body;
    private Version version;
    @JsonProperty("_links")
    private Links links;
    @JsonProperty("_expandable")
    private ExpandPage expand;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Body getBody() {
        return body;
    }

    public void setBody(Body body) {
        this.body = body;
    }

    public Version getVersion() {
        return version;
    }

    public void setVersion(Version version) {
        this.version = version;
    }

    public Links getLinks() {
        return links;
    }

    public void setLinks(Links links) {
        this.links = links;
    }

    public ExpandPage getExpand() {
        return expand;
    }

    public void setExpand(ExpandPage expand) {
        this.expand = expand;
    }
}

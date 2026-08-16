package com.mecklon.RagPoweredCodingAssistant.rag;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Structured output from the model: a natural-language summary plus a list of
 * file changes to display as diffs for accept/reject.
 */
public class ChangeResponse {

    @JsonProperty("summary")
    private String summary;

    @JsonProperty("changes")
    private List<CodeChange> changes;

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<CodeChange> getChanges() {
        return changes;
    }

    public void setChanges(List<CodeChange> changes) {
        this.changes = changes;
    }
}
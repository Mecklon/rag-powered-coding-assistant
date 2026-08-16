package com.mecklon.RagPoweredCodingAssistant.rag;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single file change produced by the model: which file changed and its old
 * vs new content (whole-file granularity).
 */
public class CodeChange {

    @JsonProperty("filePath")
    private String filePath;

    @JsonProperty("oldContent")
    private String oldContent;

    @JsonProperty("newContent")
    private String newContent;

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getOldContent() {
        return oldContent;
    }

    public void setOldContent(String oldContent) {
        this.oldContent = oldContent;
    }

    public String getNewContent() {
        return newContent;
    }

    public void setNewContent(String newContent) {
        this.newContent = newContent;
    }
}
package com.mecklon.RagPoweredCodingAssistant.chunker;

import java.util.ArrayList;
import java.util.List;

/**
 * A language-agnostic wrapper around an AST node, exposing the fields the
 * chunker needs. Implementations (e.g. tree-sitter) populate this.
 */
public class AstNode {

    private String type;
    private String name;
    private String sourceText;
    private int startLine;
    private int endLine;
    private int startColumn;
    private int endColumn;
    private final List<AstNode> children = new ArrayList<>();
    private AstNode parent;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSourceText() {
        return sourceText;
    }

    public void setSourceText(String sourceText) {
        this.sourceText = sourceText;
    }

    public int getStartLine() {
        return startLine;
    }

    public void setStartLine(int startLine) {
        this.startLine = startLine;
    }

    public int getEndLine() {
        return endLine;
    }

    public void setEndLine(int endLine) {
        this.endLine = endLine;
    }

    public int getStartColumn() {
        return startColumn;
    }

    public void setStartColumn(int startColumn) {
        this.startColumn = startColumn;
    }

    public int getEndColumn() {
        return endColumn;
    }

    public void setEndColumn(int endColumn) {
        this.endColumn = endColumn;
    }

    public List<AstNode> getChildren() {
        return children;
    }

    public void addChild(AstNode child) {
        child.parent = this;
        children.add(child);
    }

    public AstNode getParent() {
        return parent;
    }

    public void setParent(AstNode parent) {
        this.parent = parent;
    }
}
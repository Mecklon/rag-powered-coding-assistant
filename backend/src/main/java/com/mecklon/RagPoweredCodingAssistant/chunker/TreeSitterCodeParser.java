package com.mecklon.RagPoweredCodingAssistant.chunker;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.treesitter.TSLanguage;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTree;
import org.treesitter.TSTreeCursor;
import org.treesitter.TreeSitterJava;
import org.treesitter.TreeSitterJavascript;
import org.treesitter.TreeSitterPython;
import org.treesitter.TreeSitterTypescript;

/**
 * Parses source code into a language-agnostic AST using tree-sitter.
 */
@Component
public class TreeSitterCodeParser implements CodeParser {

    private final Map<String, TSLanguage> languages = new HashMap<>();

    public TreeSitterCodeParser() {
        languages.put("java", new TreeSitterJava());
        languages.put("python", new TreeSitterPython());
        languages.put("javascript", new TreeSitterJavascript());
        languages.put("js", new TreeSitterJavascript());
        languages.put("jsx", new TreeSitterJavascript());
        languages.put("typescript", new TreeSitterTypescript());
        languages.put("ts", new TreeSitterTypescript());
        languages.put("tsx", new TreeSitterTypescript());
    }

    @Override
    public ParseResult parse(String content, String language) {
        TSLanguage tsLanguage = languages.get(language == null ? "" : language.toLowerCase());
        if (tsLanguage == null) {
            return new ParseResult(true, null);
        }

        try {
            TSParser parser = new TSParser();
            parser.setLanguage(tsLanguage);
            TSTree tree = parser.parseString(null, content);
            if (tree == null) {
                return new ParseResult(true, null);
            }
            TSNode root = tree.getRootNode();
            AstNode astRoot = convert(root, tree, content);
            return new ParseResult(false, astRoot);
        } catch (Exception e) {
            return new ParseResult(true, null);
        }
    }

    private AstNode convert(TSNode node, TSTree tree, String content) {
        AstNode astNode = new AstNode();
        astNode.setType(node.getType());
        astNode.setStartLine(node.getStartPoint().getRow() + 1);
        astNode.setEndLine(node.getEndPoint().getRow() + 1);
        astNode.setStartColumn(node.getStartPoint().getColumn());
        astNode.setEndColumn(node.getEndPoint().getColumn());
        astNode.setSourceText(extractText(content, node.getStartByte(), node.getEndByte()));

        // Traverse named children using a cursor for reliable native access.
        TSTreeCursor cursor = new TSTreeCursor(node);
        if (cursor.gotoFirstChild()) {
            do {
                TSNode child = cursor.currentNode();
                if (child != null && !child.isNull() && child.isNamed()) {
                    astNode.addChild(convert(child, tree, content));
                }
            } while (cursor.gotoNextSibling());
        }
        return astNode;
    }

    private String extractText(String content, int startByte, int endByte) {
        if (startByte < 0 || endByte > content.length() || startByte > endByte) {
            return "";
        }
        return content.substring(startByte, endByte);
    }
}
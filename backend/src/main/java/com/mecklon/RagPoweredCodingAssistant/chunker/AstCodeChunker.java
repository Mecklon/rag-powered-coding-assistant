package com.mecklon.RagPoweredCodingAssistant.chunker;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Implements the AST_CODE_CHUNKER algorithm: parses a file into an AST, finds
 * structural nodes (classes, methods, functions, etc.), and produces
 * deterministic, semantically-aware code chunks with rich metadata.
 */
@Component
public class AstCodeChunker {

    private final CodeParser parser;
    private final TokenEstimator tokenEstimator;

    // Config
    private static final int MAX_CHUNK_TOKENS = 512;
    private static final int MIN_CHUNK_TOKENS = 64;
    private static final int MAX_CLASS_TOKENS = 1024;
    private static final int MAX_METHOD_TOKENS = 256;
    private static final int OVERLAP_TOKENS = 32;
    private static final boolean INCLUDE_PARENT_CONTEXT = true;

    public AstCodeChunker(CodeParser parser, TokenEstimator tokenEstimator) {
        this.parser = parser;
        this.tokenEstimator = tokenEstimator;
    }

    /**
     * Chunks a single source file into a list of CodeChunks.
     */
    public List<CodeChunk> chunkFile(SourceFile sourceFile) {
        ParseResult parseResult = parser.parse(sourceFile.content(), sourceFile.language());
        if (parseResult.isParseFailed() || parseResult.getRoot() == null) {
            return fallbackTextChunking(sourceFile);
        }

        FileContext fileContext = extractFileContext(parseResult.getRoot(), sourceFile);
        List<AstNode> structuralNodes = findStructuralNodes(parseResult.getRoot());

        List<CodeChunk> chunks = new ArrayList<>();
        for (AstNode node : structuralNodes) {
            chunks.addAll(processNode(node, fileContext, sourceFile));
        }

        chunks = mergeSmallChunks(chunks);
        chunks = enforceGlobalChunkLimits(chunks);
        chunks = attachRelationships(chunks);
        chunks = assignChunkIds(chunks);
        return chunks;
    }

    // ─────────────────────────────────────────────
    // PHASE 2 — FILE-LEVEL CONTEXT
    // ─────────────────────────────────────────────
    private FileContext extractFileContext(AstNode root, SourceFile sourceFile) {
        FileContext context = new FileContext();
        context.filePath = sourceFile.path();
        context.language = sourceFile.language();
        context.packageName = findPackageDeclaration(root);
        context.imports = findImportDeclarations(root);
        return context;
    }

    private String findPackageDeclaration(AstNode root) {
        for (AstNode node : root.getChildren()) {
            if ("package_declaration".equals(node.getType())) {
                return node.getSourceText().replace("package", "").replace(";", "").trim();
            }
        }
        return null;
    }

    private List<String> findImportDeclarations(AstNode root) {
        List<String> imports = new ArrayList<>();
        for (AstNode node : root.getChildren()) {
            if ("import_declaration".equals(node.getType())) {
                imports.add(node.getSourceText().trim());
            }
        }
        return imports;
    }

    // ─────────────────────────────────────────────
    // PHASE 3 — FIND STRUCTURAL NODES
    // ─────────────────────────────────────────────
    private List<AstNode> findStructuralNodes(AstNode root) {
        List<AstNode> nodes = new ArrayList<>();
        traverse(root, nodes);
        return nodes;
    }

    private void traverse(AstNode node, List<AstNode> nodes) {
        if (isStructural(node)) {
            nodes.add(node);
        }
        for (AstNode child : node.getChildren()) {
            traverse(child, nodes);
        }
    }

    private boolean isStructural(AstNode node) {
        String type = node.getType();
        return switch (type) {
            case "class_declaration", "interface_declaration", "enum_declaration",
                 "method_declaration", "constructor_declaration",
                 "function_declaration", "function_definition",
                 "class_definition", "struct_specifier", "struct_declaration",
                 "module_declaration", "program" -> true;
            default -> false;
        };
    }

    // ─────────────────────────────────────────────
    // PHASE 4 — PROCESS EACH STRUCTURAL NODE
    // ─────────────────────────────────────────────
    private List<CodeChunk> processNode(AstNode node, FileContext fileContext, SourceFile sourceFile) {
        String type = node.getType();
        return switch (type) {
            case "class_declaration", "class_definition" -> processClass(node, fileContext, sourceFile);
            case "interface_declaration", "enum_declaration", "struct_specifier", "struct_declaration" ->
                    processType(node, fileContext, sourceFile);
            case "method_declaration", "constructor_declaration", "function_declaration",
                 "function_definition" -> processMethod(node, fileContext, sourceFile, null);
            default -> List.of();
        };
    }

    // ─────────────────────────────────────────────
    // PHASE 5 — PROCESS A CLASS
    // ─────────────────────────────────────────────
    private List<CodeChunk> processClass(AstNode classNode, FileContext fileContext, SourceFile sourceFile) {
        String className = extractName(classNode);
        List<AstNode> fields = findDirectChildrenOfType(classNode, "field_declaration");
        List<AstNode> constructors = findDirectChildrenOfType(classNode, "constructor_declaration");
        List<AstNode> methods = findDirectChildrenOfType(classNode, "method_declaration");
        List<AstNode> nestedTypes = findDirectChildrenOfType(classNode, "class_declaration");

        int classTokens = tokenEstimator.estimateTokens(classNode.getSourceText());

        // CASE 1: SMALL CLASS
        if (classTokens <= MAX_CLASS_TOKENS) {
            CodeChunk chunk = createChunk(classNode.getSourceText(), sourceFile, fileContext, classNode);
            chunk.getMetadata().setSymbolType("class");
            chunk.getMetadata().setSymbolName(className);
            chunk.getMetadata().setParentSymbol(null);
            return List.of(chunk);
        }

        // CASE 2: LARGE CLASS
        List<CodeChunk> chunks = new ArrayList<>();
        String classContext = createClassContext(classNode, className);

        // Fields grouped into one chunk
        if (!fields.isEmpty()) {
            StringBuilder fieldContent = new StringBuilder();
            for (AstNode f : fields) {
                fieldContent.append(f.getSourceText()).append("\n");
            }
            if (tokenEstimator.estimateTokens(fieldContent.toString()) <= MAX_CHUNK_TOKENS) {
                CodeChunk fieldChunk = createChunk(classContext + fieldContent, sourceFile, fileContext, classNode);
                fieldChunk.getMetadata().setSymbolType("class_fields");
                fieldChunk.getMetadata().setSymbolName(className);
                chunks.add(fieldChunk);
            } else {
                chunks.addAll(splitLargeNode(fields, classContext, sourceFile, fileContext));
            }
        }

        for (AstNode constructor : constructors) {
            chunks.addAll(processMethod(constructor, fileContext, sourceFile, classContext));
        }
        for (AstNode method : methods) {
            chunks.addAll(processMethod(method, fileContext, sourceFile, classContext));
        }
        for (AstNode nestedType : nestedTypes) {
            chunks.addAll(processNode(nestedType, fileContext, sourceFile));
        }
        return chunks;
    }

    // ─────────────────────────────────────────────
    // PHASE 6 — PROCESS A METHOD / FUNCTION
    // ─────────────────────────────────────────────
    private List<CodeChunk> processMethod(AstNode methodNode, FileContext fileContext,
                                          SourceFile sourceFile, String parentContext) {
        String methodName = extractName(methodNode);
        String visibility = extractVisibility(methodNode);
        String returnType = extractReturnType(methodNode);
        String body = extractBody(methodNode);

        int methodTokens = tokenEstimator.estimateTokens(methodNode.getSourceText());

        // NORMAL METHOD
        if (methodTokens <= MAX_METHOD_TOKENS) {
            CodeChunk chunk = createChunk(buildMethodContent(parentContext, methodNode), sourceFile, fileContext, methodNode);
            chunk.getMetadata().setSymbolType("method");
            chunk.getMetadata().setSymbolName(methodName);
            chunk.getMetadata().setParentSymbol(parentContext);
            chunk.getMetadata().setClassName(parentContext);
            return List.of(chunk);
        }

        // LARGE METHOD — split by logical statements
        List<AstNode> statements = getLogicalStatements(body, methodNode);
        List<CodeChunk> subChunks = new ArrayList<>();
        List<AstNode> currentGroup = new ArrayList<>();

        for (AstNode statement : statements) {
            if (wouldExceedLimit(currentGroup, statement)) {
                subChunks.add(createMethodSubChunk(methodNode, currentGroup, parentContext, fileContext, sourceFile));
                currentGroup = new ArrayList<>();
            }
            currentGroup.add(statement);
        }
        if (!currentGroup.isEmpty()) {
            subChunks.add(createMethodSubChunk(methodNode, currentGroup, parentContext, fileContext, sourceFile));
        }

        List<CodeChunk> finalChunks = new ArrayList<>();
        for (CodeChunk chunk : subChunks) {
            if (tokenEstimator.estimateTokens(chunk.getContent()) > MAX_CHUNK_TOKENS) {
                finalChunks.addAll(fallbackCodeSplit(chunk, MAX_CHUNK_TOKENS, OVERLAP_TOKENS));
            } else {
                finalChunks.add(chunk);
            }
        }
        return finalChunks;
    }

    // ─────────────────────────────────────────────
    // PHASE 7 — TYPE / INTERFACE / ENUM
    // ─────────────────────────────────────────────
    private List<CodeChunk> processType(AstNode typeNode, FileContext fileContext, SourceFile sourceFile) {
        String typeName = extractName(typeNode);
        int totalTokens = tokenEstimator.estimateTokens(typeNode.getSourceText());

        if (totalTokens <= MAX_CLASS_TOKENS) {
            CodeChunk chunk = createChunk(typeNode.getSourceText(), sourceFile, fileContext, typeNode);
            chunk.getMetadata().setSymbolType(typeNode.getType());
            chunk.getMetadata().setSymbolName(typeName);
            return List.of(chunk);
        }

        List<CodeChunk> chunks = new ArrayList<>();
        String typeContext = createTypeContext(typeNode);
        for (AstNode member : typeNode.getChildren()) {
            if (isMethodLike(member.getType())) {
                chunks.addAll(processMethod(member, fileContext, sourceFile, typeContext));
            } else {
                CodeChunk chunk = createChunk(typeContext + member.getSourceText(), sourceFile, fileContext, member);
                chunks.add(chunk);
            }
        }
        return chunks;
    }

    // ─────────────────────────────────────────────
    // PHASE 8 — CREATE METADATA
    // ─────────────────────────────────────────────
    private CodeChunk createChunk(String content, SourceFile sourceFile, FileContext fileContext, AstNode node) {
        ChunkMetadata metadata = new ChunkMetadata();
        metadata.setRepositoryId(sourceFile.repositoryId());
        metadata.setBranch(sourceFile.branch());
        metadata.setFilePath(sourceFile.path());
        metadata.setLanguage(sourceFile.language());
        metadata.setStartLine(node.getStartLine());
        metadata.setEndLine(node.getEndLine());
        metadata.setStartColumn(node.getStartColumn());
        metadata.setEndColumn(node.getEndColumn());
        metadata.setNodeType(node.getType());
        metadata.setSymbolName(extractName(node));
        metadata.setParentSymbol(findParentSymbol(node));
        metadata.setSymbolType(classifySymbol(node));
        metadata.setPackageName(fileContext.packageName);
        metadata.setImports(fileContext.imports);
        return new CodeChunk(content, metadata);
    }

    // ─────────────────────────────────────────────
    // PHASE 9 — MERGE SMALL CHUNKS
    // ─────────────────────────────────────────────
    private List<CodeChunk> mergeSmallChunks(List<CodeChunk> chunks) {
        List<CodeChunk> result = new ArrayList<>();
        CodeChunk current = null;
        for (CodeChunk chunk : chunks) {
            if (current == null) {
                current = chunk;
                continue;
            }
            if (tokenEstimator.estimateTokens(current.getContent()) < MIN_CHUNK_TOKENS
                    && sameLogicalParent(current, chunk)) {
                current = merge(current, chunk);
                continue;
            }
            result.add(current);
            current = chunk;
        }
        if (current != null) {
            result.add(current);
        }
        return result;
    }

    // ─────────────────────────────────────────────
    // PHASE 10 — ENFORCE MAXIMUM SIZE
    // ─────────────────────────────────────────────
    private List<CodeChunk> enforceGlobalChunkLimits(List<CodeChunk> chunks) {
        List<CodeChunk> result = new ArrayList<>();
        for (CodeChunk chunk : chunks) {
            if (tokenEstimator.estimateTokens(chunk.getContent()) <= MAX_CHUNK_TOKENS) {
                result.add(chunk);
            } else {
                result.addAll(fallbackCodeSplit(chunk, MAX_CHUNK_TOKENS, OVERLAP_TOKENS));
            }
        }
        return result;
    }

    // ─────────────────────────────────────────────
    // PHASE 11 — ADD STRUCTURAL RELATIONSHIPS
    // ─────────────────────────────────────────────
    private List<CodeChunk> attachRelationships(List<CodeChunk> chunks) {
        for (CodeChunk chunk : chunks) {
            ChunkMetadata m = chunk.getMetadata();
            m.setParentSymbol(findNearestParentSymbol(chunk));
            m.setClassName(findContainingClass(chunk));
            m.setModule(findContainingModule(chunk));
        }
        return chunks;
    }

    // ─────────────────────────────────────────────
    // PHASE 12 — ASSIGN STABLE IDENTIFIERS
    // ─────────────────────────────────────────────
    private List<CodeChunk> assignChunkIds(List<CodeChunk> chunks) {
        for (CodeChunk chunk : chunks) {
            ChunkMetadata m = chunk.getMetadata();
            String identity = m.getRepositoryId() + ":" + m.getBranch() + ":" + m.getFilePath()
                    + ":" + m.getStartLine() + ":" + m.getEndLine() + ":" + m.getNodeType();
            m.setChunkId(hash(identity));
        }
        return chunks;
    }

    // ─────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────
    private String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.substring(0, 16);
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    private String extractName(AstNode node) {
        for (AstNode child : node.getChildren()) {
            String type = child.getType();
            if ("identifier".equals(type) || "type_identifier".equals(type)
                    || "field_identifier".equals(type) || "property_identifier".equals(type)) {
                return child.getSourceText();
            }
        }
        return null;
    }

    private String extractVisibility(AstNode node) {
        String text = node.getSourceText();
        if (text.startsWith("public")) return "public";
        if (text.startsWith("private")) return "private";
        if (text.startsWith("protected")) return "protected";
        return null;
    }

    private String extractReturnType(AstNode node) {
        String text = node.getSourceText();
        int paren = text.indexOf('(');
        if (paren < 0) return null;
        String before = text.substring(0, paren);
        String[] parts = before.trim().split("\\s+");
        if (parts.length >= 2) {
            return parts[parts.length - 2];
        }
        return null;
    }

    private String extractBody(AstNode node) {
        String text = node.getSourceText();
        int open = text.indexOf('{');
        int close = text.lastIndexOf('}');
        if (open >= 0 && close > open) {
            return text.substring(open, close + 1);
        }
        return text;
    }

    private List<AstNode> findDirectChildrenOfType(AstNode node, String type) {
        List<AstNode> result = new ArrayList<>();
        for (AstNode child : node.getChildren()) {
            if (type.equals(child.getType())) {
                result.add(child);
            }
        }
        return result;
    }

    private String createClassContext(AstNode classNode, String className) {
        StringBuilder sb = new StringBuilder();
        sb.append("// Class: ").append(className).append("\n");
        for (AstNode child : classNode.getChildren()) {
            String type = child.getType();
            if ("field_declaration".equals(type) || "annotation".equals(type)) {
                sb.append(child.getSourceText()).append("\n");
            }
        }
        return sb.toString();
    }

    private String createTypeContext(AstNode typeNode) {
        StringBuilder sb = new StringBuilder();
        sb.append("// Type: ").append(extractName(typeNode)).append("\n");
        return sb.toString();
    }

    private String buildMethodContent(String parentContext, AstNode methodNode) {
        if (INCLUDE_PARENT_CONTEXT && parentContext != null && !parentContext.isBlank()) {
            return parentContext + "\n" + methodNode.getSourceText();
        }
        return methodNode.getSourceText();
    }

    private List<AstNode> getLogicalStatements(String body, AstNode methodNode) {
        // Use the method's direct children as logical statements.
        List<AstNode> statements = new ArrayList<>();
        for (AstNode child : methodNode.getChildren()) {
            String type = child.getType();
            if (!"identifier".equals(type) && !"type_identifier".equals(type)
                    && !"formal_parameters".equals(type) && !"modifiers".equals(type)) {
                statements.add(child);
            }
        }
        return statements;
    }

    private boolean wouldExceedLimit(List<AstNode> group, AstNode statement) {
        int current = 0;
        for (AstNode s : group) {
            current += tokenEstimator.estimateTokens(s.getSourceText());
        }
        return current + tokenEstimator.estimateTokens(statement.getSourceText()) > MAX_METHOD_TOKENS;
    }

    private CodeChunk createMethodSubChunk(AstNode methodNode, List<AstNode> statements,
                                           String parentContext, FileContext fileContext, SourceFile sourceFile) {
        StringBuilder content = new StringBuilder();
        if (parentContext != null && !parentContext.isBlank()) {
            content.append(parentContext).append("\n");
        }
        content.append("// Method: ").append(extractName(methodNode)).append("\n");
        for (AstNode s : statements) {
            content.append(s.getSourceText()).append("\n");
        }
        CodeChunk chunk = createChunk(content.toString(), sourceFile, fileContext, methodNode);
        chunk.getMetadata().setSymbolType("method");
        chunk.getMetadata().setSymbolName(extractName(methodNode));
        return chunk;
    }

    private List<CodeChunk> splitLargeNode(List<AstNode> nodes, String context,
                                           SourceFile sourceFile, FileContext fileContext) {
        List<CodeChunk> chunks = new ArrayList<>();
        List<AstNode> group = new ArrayList<>();
        int tokens = 0;
        for (AstNode node : nodes) {
            int nodeTokens = tokenEstimator.estimateTokens(node.getSourceText());
            if (tokens + nodeTokens > MAX_CHUNK_TOKENS && !group.isEmpty()) {
                chunks.add(createGroupChunk(group, context, sourceFile, fileContext));
                group = new ArrayList<>();
                tokens = 0;
            }
            group.add(node);
            tokens += nodeTokens;
        }
        if (!group.isEmpty()) {
            chunks.add(createGroupChunk(group, context, sourceFile, fileContext));
        }
        return chunks;
    }

    private CodeChunk createGroupChunk(List<AstNode> group, String context,
                                       SourceFile sourceFile, FileContext fileContext) {
        StringBuilder content = new StringBuilder();
        if (context != null) {
            content.append(context);
        }
        for (AstNode node : group) {
            content.append(node.getSourceText()).append("\n");
        }
        return createChunk(content.toString(), sourceFile, fileContext, group.get(0));
    }

    private boolean sameLogicalParent(CodeChunk a, CodeChunk b) {
        return a.getMetadata().getClassName() != null
                && a.getMetadata().getClassName().equals(b.getMetadata().getClassName());
    }

    private CodeChunk merge(CodeChunk a, CodeChunk b) {
        String merged = a.getContent() + "\n" + b.getContent();
        CodeChunk chunk = new CodeChunk(merged, a.getMetadata());
        chunk.getMetadata().setEndLine(b.getMetadata().getEndLine());
        return chunk;
    }

    private List<CodeChunk> fallbackCodeSplit(CodeChunk chunk, int maxTokens, int overlapTokens) {
        List<CodeChunk> result = new ArrayList<>();
        String content = chunk.getContent();
        String[] lines = content.split("\n", -1);
        List<String> current = new ArrayList<>();
        int tokens = 0;
        for (String line : lines) {
            int lineTokens = tokenEstimator.estimateTokens(line);
            if (tokens + lineTokens > maxTokens && !current.isEmpty()) {
                result.add(buildFallbackChunk(chunk, current));
                // overlap: keep last overlapTokens worth of lines
                int keep = overlapLines(current, overlapTokens);
                current = new ArrayList<>(current.subList(Math.max(0, current.size() - keep), current.size()));
                tokens = 0;
                for (String k : current) {
                    tokens += tokenEstimator.estimateTokens(k);
                }
            }
            current.add(line);
            tokens += lineTokens;
        }
        if (!current.isEmpty()) {
            result.add(buildFallbackChunk(chunk, current));
        }
        return result;
    }

    private int overlapLines(List<String> lines, int overlapTokens) {
        int count = 0;
        int tokens = 0;
        for (int i = lines.size() - 1; i >= 0; i--) {
            tokens += tokenEstimator.estimateTokens(lines.get(i));
            count++;
            if (tokens >= overlapTokens) break;
        }
        return count;
    }

    private CodeChunk buildFallbackChunk(CodeChunk original, List<String> lines) {
        String content = String.join("\n", lines);
        CodeChunk chunk = new CodeChunk(content, original.getMetadata());
        return chunk;
    }

    private String findParentSymbol(AstNode node) {
        AstNode parent = node.getParent();
        while (parent != null) {
            if (isStructural(parent)) {
                return extractName(parent);
            }
            parent = parent.getParent();
        }
        return null;
    }

    private String classifySymbol(AstNode node) {
        String type = node.getType();
        if (type.contains("class")) return "class";
        if (type.contains("interface")) return "interface";
        if (type.contains("enum")) return "enum";
        if (type.contains("struct")) return "struct";
        if (type.contains("method") || type.contains("constructor")) return "method";
        if (type.contains("function")) return "function";
        return type;
    }

    private boolean isMethodLike(String type) {
        return type.contains("method") || type.contains("function") || type.contains("constructor");
    }

    private String findNearestParentSymbol(CodeChunk chunk) {
        return chunk.getMetadata().getParentSymbol();
    }

    private String findContainingClass(CodeChunk chunk) {
        return chunk.getMetadata().getClassName();
    }

    private String findContainingModule(CodeChunk chunk) {
        return chunk.getMetadata().getPackageName();
    }

    // ─────────────────────────────────────────────
    // FALLBACK TEXT CHUNKING
    // ─────────────────────────────────────────────
    private List<CodeChunk> fallbackTextChunking(SourceFile sourceFile) {
        List<CodeChunk> chunks = new ArrayList<>();
        String[] lines = sourceFile.content().split("\n", -1);
        List<String> current = new ArrayList<>();
        int tokens = 0;
        int startLine = 1;
        for (int i = 0; i < lines.length; i++) {
            int lineTokens = tokenEstimator.estimateTokens(lines[i]);
            if (tokens + lineTokens > MAX_CHUNK_TOKENS && !current.isEmpty()) {
                chunks.add(buildTextChunk(sourceFile, current, startLine, i));
                current = new ArrayList<>();
                tokens = 0;
                startLine = i + 1;
            }
            current.add(lines[i]);
            tokens += lineTokens;
        }
        if (!current.isEmpty()) {
            chunks.add(buildTextChunk(sourceFile, current, startLine, lines.length));
        }
        return chunks;
    }

    private CodeChunk buildTextChunk(SourceFile sourceFile, List<String> lines, int startLine, int endLine) {
        ChunkMetadata metadata = new ChunkMetadata();
        metadata.setRepositoryId(sourceFile.repositoryId());
        metadata.setBranch(sourceFile.branch());
        metadata.setFilePath(sourceFile.path());
        metadata.setLanguage(sourceFile.language());
        metadata.setStartLine(startLine);
        metadata.setEndLine(endLine);
        metadata.setNodeType("text_fallback");
        metadata.setSymbolType("text");
        return new CodeChunk(String.join("\n", lines), metadata);
    }

    // ─────────────────────────────────────────────
    // FILE CONTEXT HOLDER
    // ─────────────────────────────────────────────
    private static class FileContext {
        String filePath;
        String language;
        String packageName;
        List<String> imports;
    }
}
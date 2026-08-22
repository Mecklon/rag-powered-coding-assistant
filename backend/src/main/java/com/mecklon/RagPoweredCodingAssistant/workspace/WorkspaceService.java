package com.mecklon.RagPoweredCodingAssistant.workspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.mecklon.RagPoweredCodingAssistant.guardrails.Guard;
import com.mecklon.RagPoweredCodingAssistant.rag.RepoCloneService;

/**
 * The authoritative working copy of a repository. All file modifications go
 * through this service so that dirty files are tracked and RAG re-indexing is
 * triggered consistently, regardless of whether the change came from the user
 * or the AI.
 *
 * <p>The workspace is the single controlled path for file operations. Tools
 * must NOT manipulate the filesystem directly; they delegate here.
 */
@Service
public class WorkspaceService {

    private final RepoCloneService cloneService;
    private final Guard guard;

    public WorkspaceService(RepoCloneService cloneService, Guard guard) {
        this.cloneService = cloneService;
        this.guard = guard;
    }

    /** Returns the repo root directory for the given repo. */
    public Path repoRoot(String owner, String repo) {
        return cloneService.repoDir(owner, repo).toPath();
    }

    /** Lists all file paths (relative) in the workspace, skipping .git. */
    public List<String> listFiles(String owner, String repo) {
        Path root = repoRoot(owner, repo);
        List<String> paths = new ArrayList<>();
        try (var stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> !p.toString().contains("/.git/"))
                    .forEach(p -> paths.add(root.relativize(p).toString().replace('\\', '/')));
        } catch (IOException e) {
            throw new RuntimeException("Failed to list files for " + owner + "/" + repo, e);
        }
        return paths;
    }

    /** Reads a file's content from the workspace (authoritative current source). */
    public String readFile(String owner, String repo, String path) {
        Path root = repoRoot(owner, repo);
        Path target = guard.resolve(root, path);
        guard.validateRead(root, target);
        try {
            return Files.readString(target, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + path, e);
        }
    }

    /** Creates a new file and records a CREATE change. */
    public FileChange createFile(String owner, String repo, String path, String content) {
        Path root = repoRoot(owner, repo);
        Path target = guard.resolve(root, path);
        guard.validateCreate(root, target);
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content == null ? "" : content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create file: " + path, e);
        }
        FileChange change = new FileChange(path, "CREATE", null, content == null ? "" : content);
        recordChange(owner, repo, change);
        return change;
    }

    /**
     * Applies a granular edit (oldText -> newText) to a file. The replacement
     * must be unambiguous. Records a MODIFY change.
     */
    public FileChange editFile(String owner, String repo, String path, String oldText, String newText) {
        Path root = repoRoot(owner, repo);
        Path target = guard.resolve(root, path);
        guard.validateEdit(root, target);
        String before;
        try {
            before = Files.readString(target, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + path, e);
        }
        guard.validateEditUnambiguous(before, oldText);
        String after = before.replace(oldText, newText);
        try {
            Files.writeString(target, after, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write file: " + path, e);
        }
        FileChange change = new FileChange(path, "MODIFY", before, after);
        recordChange(owner, repo, change);
        return change;
    }

    /** Deletes a file and records a DELETE change. */
    public FileChange deleteFile(String owner, String repo, String path) {
        Path root = repoRoot(owner, repo);
        Path target = guard.resolve(root, path);
        guard.validateDelete(root, target);
        String before;
        try {
            before = Files.readString(target, StandardCharsets.UTF_8);
            Files.delete(target);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete file: " + path, e);
        }
        FileChange change = new FileChange(path, "DELETE", before, null);
        recordChange(owner, repo, change);
        return change;
    }

    /** Renames a file and records a RENAME change. */
    public FileChange renameFile(String owner, String repo, String source, String destination) {
        Path root = repoRoot(owner, repo);
        Path src = guard.resolve(root, source);
        Path dst = guard.resolve(root, destination);
        guard.validateRename(root, src, dst);
        String before;
        try {
            before = Files.readString(src, StandardCharsets.UTF_8);
            Files.createDirectories(dst.getParent());
            Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to rename file: " + source + " -> " + destination, e);
        }
        FileChange change = new FileChange(source, "RENAME", before, destination);
        recordChange(owner, repo, change);
        return change;
    }

    /**
     * Applies a set of dirty files (from the frontend) to the workspace. This
     * is the user-driven path; each write is recorded as a MODIFY change.
     */
    public List<FileChange> applyDirtyFiles(String owner, String repo, List<DirtyFile> dirtyFiles) {
        List<FileChange> changes = new ArrayList<>();
        for (DirtyFile dirty : dirtyFiles) {
            Path root = repoRoot(owner, repo);
            Path target = guard.resolve(root, dirty.path());
            String before = Files.exists(target)
                    ? readFile(owner, repo, dirty.path())
                    : null;
            try {
                Files.createDirectories(target.getParent());
                Files.writeString(target, dirty.content(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("Failed to write dirty file " + dirty.path(), e);
            }
            changes.add(new FileChange(dirty.path(), "MODIFY", before, dirty.content()));
        }
        return changes;
    }

    /** Returns the set of file paths that have been modified since the last index. */
    public Set<String> dirtyFiles(String owner, String repo) {
        return dirtyByRepo.computeIfAbsent(repoKey(owner, repo), k -> new LinkedHashSet<>());
    }

    /** Marks a file as dirty (modified but not yet re-indexed). */
    public void markDirty(String owner, String repo, String path) {
        dirtyFiles(owner, repo).add(path);
    }

    /** Clears the dirty set for a repo after re-indexing. */
    public void clearDirty(String owner, String repo) {
        dirtyFiles(owner, repo).clear();
    }

    /** Records a file change and marks the file dirty. */
    public void recordChange(String owner, String repo, FileChange change) {
        changesByRepo.computeIfAbsent(repoKey(owner, repo), k -> new ArrayList<>()).add(change);
        if (change.path() != null) {
            markDirty(owner, repo, change.path());
        }
        if (change.operation().equals("RENAME") && change.after() != null) {
            markDirty(owner, repo, change.after());
        }
    }

    /** Returns and clears the recorded file changes for a repo. */
    public List<FileChange> drainChanges(String owner, String repo) {
        List<FileChange> changes = changesByRepo.remove(repoKey(owner, repo));
        return changes == null ? List.of() : changes;
    }

    private final java.util.Map<String, Set<String>> dirtyByRepo = new java.util.HashMap<>();
    private final java.util.Map<String, List<FileChange>> changesByRepo = new java.util.HashMap<>();

    private String repoKey(String owner, String repo) {
        return owner + "/" + repo;
    }
}
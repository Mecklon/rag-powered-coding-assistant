package com.mecklon.RagPoweredCodingAssistant.rag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.stereotype.Service;

/**
 * Clones a repository into an isolated directory on the backend's filesystem
 * using JGit. Each repo (owner/repo) gets its own directory under a base root.
 */
@Service
public class RepoCloneService {

    private final String baseDir;

    public RepoCloneService() {
        // Isolated root under the backend's working directory.
        this.baseDir = System.getProperty("user.dir") + java.io.File.separator + "repos";
    }

    /**
     * Returns the local directory for the given repo.
     */
    public java.io.File repoDir(String owner, String repo) {
        return new java.io.File(baseDir, owner + "__" + repo);
    }

    /**
     * Returns the target branch name (MecklonsRAGIDE.dot form) or the default.
     */
    private String targetBranch(String defaultBranch) {
        return "MecklonsRAGIDE." + defaultBranch;
    }

    /**
     * Checks if a repo is already cloned at the expected location.
     */
    public boolean isCloned(String owner, String repo) {
        java.io.File dir = repoDir(owner, repo);
        return dir.exists() && new java.io.File(dir, ".git").exists();
    }

    /**
     * Clones the repository from GitHub. Uses default remote + branch.
     * AccessToken is optional (public repos clone without auth).
     */
    public java.io.File cloneRepo(String owner, String repo, String defaultBranch) throws GitAPIException, IOException {
        java.io.File dir = repoDir(owner, repo);
        if (dir.exists()) {
            Path dirPath = dir.toPath();
            Files.walk(dirPath).sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> p.toFile().delete());
        }
        Files.createDirectories(dir.getParentFile().toPath());

        try (Git git = Git.cloneRepository()
                .setURI("https://github.com/" + owner + "/" + repo + ".git")
                .setDirectory(dir)
                .setCloneAllBranches(false)
                .setNoCheckout(false)
                .call()) {
            return dir;
        }
    }
}
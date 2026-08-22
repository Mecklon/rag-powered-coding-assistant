package com.mecklon.RagPoweredCodingAssistant.agent;

import java.util.ArrayList;
import java.util.List;

import com.mecklon.RagPoweredCodingAssistant.workspace.FileChange;

/**
 * In-memory state for a single agent run. The agent is a controller around the
 * LLM, not the LLM itself, so it tracks the request, tool history, modified
 * files, and iteration count.
 */
public class AgentState {

    private final String owner;
    private final String repo;
    private final String sessionId;
    private final String userRequest;
    private final List<FileChange> fileChanges = new ArrayList<>();
    private int iteration = 0;
    private boolean finished = false;

    public AgentState(String owner, String repo, String sessionId, String userRequest) {
        this.owner = owner;
        this.repo = repo;
        this.sessionId = sessionId;
        this.userRequest = userRequest;
    }

    public String owner() {
        return owner;
    }

    public String repo() {
        return repo;
    }

    public String sessionId() {
        return sessionId;
    }

    public String userRequest() {
        return userRequest;
    }

    public List<FileChange> fileChanges() {
        return fileChanges;
    }

    public void addFileChange(FileChange change) {
        fileChanges.add(change);
    }

    public int iteration() {
        return iteration;
    }

    public void incrementIteration() {
        iteration++;
    }

    public boolean finished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }
}
import { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { FaArrowLeft, FaCodeBranch, FaExclamationTriangle, FaPaperPlane, FaSpinner } from "react-icons/fa";
import Editor, { DiffEditor } from "@monaco-editor/react";
import api from "../api/api";
import FileTree from "../components/FileTree";

const IdePage = () => {
  const { owner, repo } = useParams();
  const navigate = useNavigate();

  const [checking, setChecking] = useState(true);
  const [hasBranch, setHasBranch] = useState(false);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState(null);
  const [selectedFile, setSelectedFile] = useState(null);
  const [chatInput, setChatInput] = useState("");

  // File explorer state
  const [paths, setPaths] = useState([]);
  const [treeLoading, setTreeLoading] = useState(false);
  // path -> content (only loaded files)
  const [fileContents, setFileContents] = useState({});
  // set of paths that have been edited (dirty)
  const [dirtyFiles, setDirtyFiles] = useState(new Set());
  const [fileLoading, setFileLoading] = useState(false);

  // Per-repo conversation session id, persisted in localStorage.
  const [sessionId, setSessionId] = useState(() => {
    const key = `rag_session_${owner}_${repo}`;
    let existing = localStorage.getItem(key);
    if (!existing) {
      existing = `session_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`;
      localStorage.setItem(key, existing);
    }
    return existing;
  });

  // AI chat response state: summary text + streaming tool events.
  const [chatSummary, setChatSummary] = useState("");
  const [toolEvents, setToolEvents] = useState([]);
  const [proposedChanges, setProposedChanges] = useState([]);
  const [acceptedChanges, setAcceptedChanges] = useState(new Set());
  const [rejectedChanges, setRejectedChanges] = useState(new Set());
  // Index of the proposed change currently shown in the Monaco diff editor.
  const [reviewIndex, setReviewIndex] = useState(null);

  // Maps a file path/name to a Monaco language id.
  const guessLanguage = (path) => {
    if (!path) return "plaintext";
    const ext = path.split(".").pop().toLowerCase();
    const map = {
      js: "javascript", jsx: "javascript", ts: "typescript", tsx: "typescript",
      json: "json", html: "html", css: "css", xml: "xml", md: "markdown",
      java: "java", py: "python", c: "c", cpp: "cpp", h: "cpp", cs: "csharp",
      sh: "shell", yml: "yaml", yaml: "yaml", sql: "sql", php: "php",
      rb: "ruby", go: "go", rs: "rust",
    };
    return map[ext] || "plaintext";
  };

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await api.get(`/api/github/repos/${owner}/${repo}/rag-branch`);
        if (!cancelled) setHasBranch(res.data?.exists === true);
      } catch (e) {
        if (!cancelled) setError("Failed to check branch status.");
      } finally {
        if (!cancelled) setChecking(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [owner, repo]);

  // Fetch the file tree once the RAG branch is confirmed.
  useEffect(() => {
    if (!hasBranch) return;
    let cancelled = false;
    (async () => {
      setTreeLoading(true);
      try {
        const res = await api.get(`/api/github/repos/${owner}/${repo}/tree`);
        if (!cancelled) setPaths(res.data?.paths || []);
      } catch (e) {
        if (!cancelled) setError("Failed to load file tree.");
      } finally {
        if (!cancelled) setTreeLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [hasBranch, owner, repo]);

  // Load a single file's content when selected.
  const handleSelectFile = async (path) => {
    setSelectedFile(path);
    if (fileContents[path] !== undefined) return; // already loaded
    setFileLoading(true);
    try {
      const res = await api.post(
        `/api/github/repos/${owner}/${repo}/content`,
        { path }
      );
      setFileContents((prev) => ({ ...prev, [path]: res.data?.content ?? "" }));
    } catch (e) {
      setError("Failed to load file content.");
    } finally {
      setFileLoading(false);
    }
  };

  // Mark a file dirty when its editor content changes.
  const handleEditorChange = (value) => {
    if (!selectedFile) return;
    setFileContents((prev) => ({ ...prev, [selectedFile]: value ?? "" }));
    setDirtyFiles((prev) => {
      const next = new Set(prev);
      next.add(selectedFile);
      return next;
    });
  };

  const handleCreateBranch = async () => {
    setCreating(true);
    setError(null);
    try {
      const res = await api.post(`/api/github/repos/${owner}/${repo}/rag-branch`);
      if (res.data?.branch) {
        setHasBranch(true);
      }
    } catch (e) {
      setError("Failed to create the RAG IDE branch.");
    } finally {
      setCreating(false);
    }
  };

  // Send a chat prompt to the agent loop.
  const [chatLoading, setChatLoading] = useState(false);
  const handleChatSubmit = async () => {
    const prompt = chatInput.trim();
    if (!prompt || chatLoading) return;
    console.log("sending")

    setChatLoading(true);
    setChatSummary("");
    setToolEvents([]);
    setProposedChanges([]);
    setAcceptedChanges(new Set());
    setChatInput("");
    try {
      // Stream the agent run via SSE. Each event is a JSON object.
      const res = await fetch("http://localhost:9090/api/agent/run", {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ owner, repo, prompt, sessionId }),
      });
      if (!res.ok || !res.body) {
        throw new Error("Agent request failed");
      }
      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        // SSE events are separated by blank lines.
        const events = buffer.split(/\n\n/);
        buffer = events.pop();
        for (const evt of events) {
          const dataLine = evt.split("\n").find((l) => l.startsWith("data:"));
          if (!dataLine) continue;
          const payload = JSON.parse(dataLine.slice(5).trim());
          handleAgentEvent(payload);
        }
      }
      // Flush any remaining event that didn't end with a blank line.
      if (buffer.trim()) {
        const dataLine = buffer.split("\n").find((l) => l.startsWith("data:"));
        if (dataLine) {
          const payload = JSON.parse(dataLine.slice(5).trim());
          handleAgentEvent(payload);
        }
      }
    } catch (e) {
      setError("Failed to send prompt.");
    } finally {
      setChatLoading(false);
    }
  };

  // Handle a single SSE event from the agent loop.
  const handleAgentEvent = (payload) => {
    if (payload.type === "TOOL_START") {
      setToolEvents((prev) => [
        ...prev,
        { tool: payload.tool, message: payload.message },
      ]);
    } else if (payload.type === "DONE") {
      setChatSummary(payload.summary || "No response.");
    } else if (payload.type === "ERROR") {
      setError(payload.message || "Agent error.");
    }
  };

  // Accept a proposed change: update the editor content, unmark dirty, and
  // tell the backend to refresh + re-vectorize the file.
  // Find the next unresolved proposed change to review, wrapping around.
  const findNextReview = (currentIndex, accepted, rejected) => {
    for (let i = currentIndex + 1; i < proposedChanges.length; i++) {
      if (!accepted.has(i) && !rejected.has(i)) return i;
    }
    for (let i = 0; i < proposedChanges.length; i++) {
      if (!accepted.has(i) && !rejected.has(i)) return i;
    }
    return null;
  };

  const handleAcceptChange = async (change, index) => {
    const path = change.filePath;
    // Update the editor content with the new content.
    setFileContents((prev) => ({ ...prev, [path]: change.newContent ?? "" }));
    // The accepted content is now the source of truth for this file.
    setDirtyFiles((prev) => {
      const next = new Set(prev);
      next.delete(path);
      return next;
    });
    const newAccepted = new Set(acceptedChanges).add(index);
    setAcceptedChanges(newAccepted);
    try {
      await api.post("/api/chat/accept", {
        owner,
        repo,
        filePath: path,
        newContent: change.newContent ?? "",
      });
    } catch (e) {
      setError("Failed to accept change.");
    }
    setReviewIndex(findNextReview(index, newAccepted, rejectedChanges));
  };

  // Reject a proposed change: mark it as rejected (no backend call).
  const handleRejectChange = (index) => {
    const newRejected = new Set(rejectedChanges).add(index);
    setRejectedChanges(newRejected);
    setReviewIndex(findNextReview(index, acceptedChanges, newRejected));
  };

  // The proposed change currently shown in the diff editor (if any).
  const reviewChange =
    reviewIndex !== null ? proposedChanges[reviewIndex] : null;

  return (
    <div className="flex h-screen flex-col bg-bg">
      {/* Header */}
      <header className="flex items-center justify-between border-b border-border bg-bg-secondary px-6 py-3">
        <div className="flex items-center gap-3">
          <button
            onClick={() => navigate("/")}
            className="flex items-center gap-2 rounded-md border border-border bg-bg-tertiary px-3 py-1.5 text-sm font-medium text-text-primary transition-colors hover:border-accent-border hover:text-accent"
          >
            <FaArrowLeft />
            Back
          </button>
          <span className="text-lg font-semibold text-text-primary">
            {owner}/{repo}
          </span>
        </div>
      </header>

      {/* Main - fill remaining height */}
      <main className="flex min-h-0 flex-1">
        {checking ? (
          <div className="flex flex-1 items-center justify-center">
          <div className="flex items-center gap-3 text-text-secondary">
            <FaSpinner className="animate-spin text-accent" />
            Checking RAG IDE branch...
          </div>
          </div>
        ) : hasBranch ? (
          <div className="flex h-full w-full gap-0">
            {/* Left: file explorer */}
            <aside className="flex w-64 shrink-0 flex-col border-r border-border bg-bg-secondary">
              <div className="border-b border-border px-4 py-2 text-xs font-semibold uppercase tracking-wide text-text-secondary">
                Explorer
              </div>
              {treeLoading ? (
                <div className="flex items-center gap-2 p-4 text-sm text-text-secondary">
                  <FaSpinner className="animate-spin text-accent" />
                  Loading tree...
                </div>
              ) : (
                <div className="flex min-h-0 flex-1 flex-col">
                  <FileTree paths={paths} onSelectFile={handleSelectFile} />
                </div>
              )}
            </aside>

            {/* Center: Monaco editor / diff review */}
            <main className="flex-1 overflow-hidden bg-bg">
              <div className="flex h-full flex-col">
                <div className="flex items-center justify-between border-b border-border bg-bg-secondary px-4 py-2">
                  <span className="text-sm font-medium text-text-primary">
                    {reviewChange
                      ? `Review: ${reviewChange.filePath}`
                      : selectedFile || (owner && repo && `${owner}/${repo}`)}
                  </span>
                  {!reviewChange && dirtyFiles.has(selectedFile) && (
                    <span className="rounded-full border border-accent-border bg-accent-subtle px-2 py-0.5 text-xs text-accent">
                      ● dirty
                    </span>
                  )}
                </div>

                {reviewChange ? (
                  <>
                    {/* File selector when multiple changes are proposed */}
                    {proposedChanges.length > 1 && (
                      <div className="flex items-center gap-1 overflow-x-auto border-b border-border bg-bg-secondary px-3 py-1.5">
                        {proposedChanges.map((c, i) => {
                          const resolved = acceptedChanges.has(i) || rejectedChanges.has(i);
                          return (
                            <button
                              key={i}
                              onClick={() => setReviewIndex(i)}
                              className={`shrink-0 rounded-md border px-2 py-1 text-xs font-mono ${
                                i === reviewIndex
                                  ? "border-accent bg-accent-subtle text-accent"
                                  : "border-border bg-bg-tertiary text-text-secondary hover:bg-bg-tertiary"
                              }`}
                            >
                              {c.filePath}
                              {acceptedChanges.has(i) ? " ✓" : rejectedChanges.has(i) ? " ✗" : ""}
                            </button>
                          );
                        })}
                      </div>
                    )}

                    <DiffEditor
                      original={reviewChange.oldContent ?? ""}
                      modified={reviewChange.newContent ?? ""}
                      language={guessLanguage(reviewChange.filePath)}
                      theme="vs-dark"
                      height="100%"
                      options={{
                        fontSize: 14,
                        minimap: { enabled: true },
                        automaticLayout: true,
                      }}
                    />

                    {/* Accept / Reject bar */}
                    <div className="flex items-center gap-2 border-t border-border bg-bg-secondary px-4 py-2">
                      <span className="truncate font-mono text-xs text-text-secondary">
                        {reviewChange.filePath}
                      </span>
                      <div className="flex-1" />
                      {acceptedChanges.has(reviewIndex) || rejectedChanges.has(reviewIndex) ? (
                        <span className="text-xs font-semibold text-text-muted">
                          {acceptedChanges.has(reviewIndex) ? "Accepted" : "Rejected"}
                        </span>
                      ) : (
                        <>
                          <button
                            onClick={() => handleRejectChange(reviewIndex)}
                            className="rounded-md border border-border bg-bg-tertiary px-3 py-1.5 text-sm text-text-primary hover:border-danger hover:text-danger"
                          >
                            Reject
                          </button>
                          <button
                            onClick={() => handleAcceptChange(reviewChange, reviewIndex)}
                            className="rounded-md bg-accent px-3 py-1.5 text-sm font-semibold text-white hover:bg-accent-hover"
                          >
                            Accept
                          </button>
                        </>
                      )}
                    </div>
                  </>
                ) : !selectedFile ? (
                  <div className="flex flex-1 items-center justify-center text-text-muted">
                    Select a file from the explorer
                  </div>
                ) : fileLoading ? (
                  <div className="flex flex-1 items-center justify-center gap-2 text-text-secondary">
                    <FaSpinner className="animate-spin text-accent" />
                    Loading file...
                  </div>
                ) : (
                  <Editor
                    height="100%"
                    theme="vs-dark"
                    language={guessLanguage(selectedFile)}
                    path={selectedFile}
                    value={fileContents[selectedFile] ?? ""}
                    onChange={handleEditorChange}
                    options={{
                      fontSize: 14,
                      minimap: { enabled: true },
                      automaticLayout: true,
                    }}
                  />
                )}
              </div>
            </main>

            {/* Right panel: AI chat */}
            <aside className="flex w-80 shrink-0 flex-col border-l border-border bg-bg-secondary">
              <div className="border-b border-border px-4 py-2 text-xs font-semibold uppercase tracking-wide text-text-secondary">
                AI Assistant
              </div>
              <div className="flex-1 overflow-y-auto p-4 text-sm">
                {chatLoading ? (
                  <div className="space-y-2">
                    <p className="text-text-secondary">Thinking...</p>
                    {toolEvents.map((evt, i) => (
                      <div
                        key={i}
                        className="flex items-center gap-2 rounded-md border border-border bg-bg-tertiary px-2 py-1.5 text-xs"
                      >
                        <FaSpinner className="animate-spin text-accent" />
                        <span className="font-mono text-accent">{evt.tool}</span>
                        <span className="text-text-secondary">{evt.message}</span>
                      </div>
                    ))}
                  </div>
                ) : chatSummary ? (
                  <p className="whitespace-pre-wrap text-text-primary">{chatSummary}</p>
                ) : (
                  <p className="text-text-secondary">Ask the assistant about your code.</p>
                )}
              </div>
              <div className="flex items-center gap-2 border-t border-border p-3">
                <input
                  value={chatInput}
                  onChange={(e) => setChatInput(e.target.value)}
                  onKeyDown={(e) => e.key === "Enter" && handleChatSubmit()}
                  placeholder="Chat input..."
                  disabled={chatLoading}
                  className="flex-1 rounded-md border border-border bg-bg-tertiary px-3 py-2 text-sm text-text-primary placeholder:text-text-muted focus:border-accent-border focus:outline-none"
                />
                <button
                  onClick={handleChatSubmit}
                  disabled={chatLoading || !chatInput.trim()}
                  className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-accent text-white transition-colors hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-50"
                  title="Send"
                >
                  <FaPaperPlane />
                </button>
              </div>
            </aside>
          </div>
        ) : (
          <div className="flex flex-1 items-center justify-center px-6">
          <div className="w-full max-w-md rounded-lg border border-border bg-bg-secondary p-8 text-center">
            <FaCodeBranch className="mx-auto mb-4 text-4xl text-accent" />
            <h2 className="text-xl font-semibold text-text-primary">
              No RAG IDE branch found
            </h2>
            <p className="mt-2 text-sm text-text-secondary">
              This repository doesn't have a{" "}
              <span className="font-mono text-accent">MecklonsRAGIDE.*</span>{" "}
              branch yet. Create one to start working in the IDE.
            </p>

            {error && (
              <div className="mt-4 flex items-center justify-center gap-2 rounded-md border border-border bg-bg-tertiary px-4 py-2 text-sm text-danger">
                <FaExclamationTriangle />
                {error}
              </div>
            )}

            <button
              onClick={handleCreateBranch}
              disabled={creating}
              className="mt-6 flex w-full items-center justify-center gap-2 rounded-md bg-accent px-4 py-2.5 text-base font-semibold text-white transition-colors hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-60"
            >
              {creating ? (
                <>
                  <FaSpinner className="animate-spin" />
                  Creating branch...
                </>
              ) : (
                <>
                  <FaCodeBranch />
                  Create RAG IDE branch
                </>
              )}
            </button>
          </div>
          </div>
        )}
      </main>
    </div>
  );
};

export default IdePage;
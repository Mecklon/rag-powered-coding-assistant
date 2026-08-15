import { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { FaArrowLeft, FaCodeBranch, FaExclamationTriangle, FaSpinner } from "react-icons/fa";
import Editor from "@monaco-editor/react";
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
            <aside className="w-64 shrink-0 border-r border-border bg-bg-secondary">
              <div className="border-b border-border px-4 py-2 text-xs font-semibold uppercase tracking-wide text-text-secondary">
                Explorer
              </div>
              <FileTree onSelectFile={setSelectedFile} />
            </aside>

            {/* Center: Monaco editor */}
            <main className="flex-1 overflow-hidden bg-bg">
              <div className="flex h-full flex-col">
                <div className="flex items-center justify-between border-b border-border bg-bg-secondary px-4 py-2">
                  <span className="text-sm font-medium text-text-primary">
                    {selectedFile || (owner && repo && `${owner}/${repo}`)}
                  </span>
                </div>
                <Editor
                  height="100%"
                  theme="vs-dark"
                  defaultLanguage={guessLanguage(selectedFile)}
                  path={selectedFile || undefined}
                  defaultValue={
                    selectedFile
                      ? `// ${selectedFile}`
                      : "// Select a file from the explorer"
                  }
                  options={{
                    fontSize: 14,
                    minimap: { enabled: true },
                    automaticLayout: true,
                  }}
                />
              </div>
            </main>

            {/* Right panel: AI chat */}
            <aside className="flex w-80 shrink-0 flex-col border-l border-border bg-bg-secondary">
              <div className="border-b border-border px-4 py-2 text-xs font-semibold uppercase tracking-wide text-text-secondary">
                AI Assistant
              </div>
              <div className="flex-1 overflow-y-auto p-4 text-sm text-text-secondary">
                Ask the assistant about your code.
              </div>
              <div className="flex items-center gap-2 border-t border-border p-3">
                <input
                  value={chatInput}
                  onChange={(e) => setChatInput(e.target.value)}
                  placeholder="Chat input..."
                  className="flex-1 rounded-md border border-border bg-bg-tertiary px-3 py-2 text-sm text-text-primary placeholder:text-text-muted focus:border-accent-border focus:outline-none"
                />
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
              <span className="font-mono text-accent">MecklonsRAGIDE/*</span>{" "}
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
import { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { FaArrowLeft, FaCodeBranch, FaExclamationTriangle, FaSpinner } from "react-icons/fa";
import api from "../api/api";

const IdePage = () => {
  const { owner, repo } = useParams();
  const navigate = useNavigate();

  const [checking, setChecking] = useState(true);
  const [hasBranch, setHasBranch] = useState(false);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState(null);

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
    <div className="flex min-h-screen flex-col bg-bg">
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

      {/* Main */}
      <main className="flex flex-1 items-center justify-center px-6">
        {checking ? (
          <div className="flex items-center gap-3 text-text-secondary">
            <FaSpinner className="animate-spin text-accent" />
            Checking RAG IDE branch...
          </div>
        ) : hasBranch ? (
          <div className="flex items-center gap-3 rounded-lg border border-accent-border bg-accent-subtle px-6 py-4 text-accent">
            <FaCodeBranch />
            RAG IDE branch is ready.
          </div>
        ) : (
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
        )}
      </main>
    </div>
  );
};

export default IdePage;
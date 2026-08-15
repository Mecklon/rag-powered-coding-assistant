import { motion } from "motion/react";
import {
  FaGithub,
  FaSignOutAlt,
  FaRobot,
  FaLock,
  FaCodeBranch,
  FaArchive,
  FaExclamationTriangle,
} from "react-icons/fa";
import { useSelector } from "react-redux";
import { useNavigate } from "react-router-dom";
import useLogout from "../hooks/useLogout";
import useGetRepositories from "../hooks/useGetRepositories";

const HomePage = () => {
  const user = useSelector((state) => state.user.user);
  const logout = useLogout();
  const navigate = useNavigate();
  const { repos, loading, error } = useGetRepositories();
  console.log(repos)
  console.log(error)

  return (
    <div className="flex min-h-screen flex-col bg-bg">
      {/* Header */}
      <header className="flex items-center justify-between border-b border-border bg-bg-secondary px-6 py-3">
        <div className="flex items-center gap-2">
          <FaRobot className="text-2xl text-accent" />
          <span className="text-lg font-semibold text-text-primary">
            RAG Coding Assistant
          </span>
        </div>

        <div className="flex items-center gap-4">
          <div className="flex items-center gap-3">
            {user?.avatarUrl && (
              <img
                src={user.avatarUrl}
                alt="avatar"
                className="h-8 w-8 rounded-full border border-border"
              />
            )}
            <div className="flex flex-col leading-tight">
              <span className="text-sm font-medium text-text-primary">
                {user?.name || user?.login}
              </span>
              <span className="text-xs text-text-secondary">
                {user?.email || `@${user?.login}`}
              </span>
            </div>
          </div>
          <button
            onClick={logout}
            className="flex items-center gap-2 rounded-md border border-border bg-bg-tertiary px-3 py-1.5 text-sm font-medium text-text-primary transition-colors hover:border-accent-border hover:text-accent"
          >
            <FaSignOutAlt />
            Logout
          </button>
        </div>
      </header>

      {/* Main */}
      <main className="flex flex-1 flex-col px-6 py-8">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4 }}
          className="mx-auto w-full max-w-4xl"
        >
          <h1 className="text-3xl font-bold text-text-primary">
            Welcome, {user?.name?.split(" ")[0] || user?.login} 👋
          </h1>
          <p className="mt-2 text-text-secondary">
            Select a repository to start working with your RAG coding
            assistant.
          </p>

          {/* Repository list */}
          <div className="mt-8">
            <h2 className="mb-4 text-lg font-semibold text-text-primary">
              Your Repositories
            </h2>

            {loading && (
              <div className="flex items-center gap-3 rounded-lg border border-border bg-bg-secondary p-6 text-text-secondary">
                <div className="h-5 w-5 animate-spin rounded-full border-2 border-accent border-t-transparent" />
                Loading repositories...
              </div>
            )}

            {error && (
              <div className="flex items-center gap-3 rounded-lg border border-border bg-bg-secondary p-6 text-danger">
                <FaExclamationTriangle />
                Failed to load repositories.
              </div>
            )}

            {!loading && !error && repos?.length === 0 && (
              <div className="rounded-lg border border-border bg-bg-secondary p-6 text-text-secondary">
                No repositories found.
              </div>
            )}

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              {!loading &&
                !error &&
                repos?.map((repo) => (
                  <div
                    key={repo.id}
                    onClick={() => {
                      const [owner] = (repo.fullName || "").split("/");
                      navigate(`/ide/${owner}/${repo.name}`);
                    }}
                    className="flex cursor-pointer flex-col rounded-lg border border-border bg-bg-secondary p-5 transition-colors hover:border-accent-border"
                  >
                    <div className="flex items-center justify-between gap-2">
                      <div className="flex items-center gap-2 overflow-hidden">
                        <FaGithub className="shrink-0 text-accent" />
                        <a
                          href={repo.htmlUrl}
                          target="_blank"
                          rel="noreferrer"
                          className="truncate font-semibold text-accent hover:underline"
                        >
                          {repo.name}
                        </a>
                        <span className="text-xs text-text-muted">
                          {repo.isPrivate && <FaLock />}
                        </span>
                      </div>
                      {repo.fork && (
                        <span className="flex items-center gap-1 rounded-full border border-border px-2 py-0.5 text-xs text-text-secondary">
                          <FaCodeBranch /> fork
                        </span>
                      )}
                      {repo.archived && (
                        <span className="flex items-center gap-1 rounded-full border border-accent-border bg-accent-subtle px-2 py-0.5 text-xs text-accent">
                          <FaArchive /> archived
                        </span>
                      )}
                    </div>

                    {repo.description && (
                      <p className="mt-3 line-clamp-2 text-sm text-text-secondary">
                        {repo.description}
                      </p>
                    )}

                    <div className="mt-4 flex items-center gap-4 text-xs text-text-secondary">
                      {repo.language && (
                        <span className="flex items-center gap-1.5">
                          <span className="h-2 w-2 rounded-full bg-accent" />
                          {repo.language}
                        </span>
                      )}
                      {repo.defaultBranch && (
                        <span className="flex items-center gap-1">
                          <FaCodeBranch /> {repo.defaultBranch}
                        </span>
                      )}
                      {repo.isPrivate && (
                        <span className="flex items-center gap-1">
                          <FaLock /> Private
                        </span>
                      )}
                    </div>
                  </div>
                ))}
            </div>
          </div>
        </motion.div>
      </main>
    </div>
  );
};

export default HomePage;

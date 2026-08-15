import { motion } from "motion/react";
import { FaGithub, FaSignOutAlt, FaRobot } from "react-icons/fa";
import { useSelector } from "react-redux";
import useLogout from "../hooks/useLogout";

const HomePage = () => {
  const user = useSelector((state) => state.user.user);
  const logout = useLogout();

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
      <main className="flex flex-1 flex-col items-center justify-center px-6">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4 }}
          className="max-w-2xl text-center"
        >
          <div className="mb-6 flex items-center justify-center gap-2">
            <FaGithub className="text-5xl text-accent" />
          </div>
          <h1 className="text-4xl font-bold text-text-primary">
            Welcome, {user?.name?.split(" ")[0] || user?.login} 👋
          </h1>
          <p className="mt-4 text-lg text-text-secondary">
            Your coding assistant is ready. Ask anything about your codebase
            and get answers grounded in your repositories.
          </p>

          <div className="mt-10 grid grid-cols-1 gap-4 sm:grid-cols-3">
            {[
              { title: "Ask Anything", desc: "Query your code in natural language." },
              { title: "RAG Powered", desc: "Answers grounded in your repositories." },
              { title: "GitHub Integrated", desc: "Authenticated via GitHub OAuth." },
            ].map((feature) => (
              <div
                key={feature.title}
                className="rounded-lg border border-border bg-bg-secondary p-5 text-left transition-colors hover:border-accent-border"
              >
                <h3 className="font-semibold text-accent">{feature.title}</h3>
                <p className="mt-2 text-sm text-text-secondary">{feature.desc}</p>
              </div>
            ))}
          </div>
        </motion.div>
      </main>
    </div>
  );
};

export default HomePage;

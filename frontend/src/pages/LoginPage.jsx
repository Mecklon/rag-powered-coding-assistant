import { motion } from "motion/react";
import { FaGithub } from "react-icons/fa";

const LoginPage = () => {
  const githubLoginUrl = "http://localhost:9090/oauth2/authorization/github";

  return (
    <div className="flex min-h-screen items-center justify-center bg-bg px-4">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4 }}
        className="w-full max-w-md rounded-lg border border-border bg-bg-secondary p-8 shadow-xl"
      >
        <div className="mb-8 flex flex-col items-center gap-3">
          <motion.div
            animate={{ y: [0, -6, 0] }}
            transition={{ repeat: Infinity, duration: 3, ease: "easeInOut" }}
            className="flex h-16 w-16 items-center justify-center rounded-xl bg-accent-subtle border border-accent-border"
          >
            <FaGithub className="text-4xl text-accent" />
          </motion.div>
          <h1 className="text-2xl font-semibold text-text-primary">
            RAG Coding Assistant
          </h1>
          <p className="text-sm text-text-secondary">
            Sign in with GitHub to continue
          </p>
        </div>

        <a
          href={githubLoginUrl}
          className="flex w-full items-center justify-center gap-3 rounded-md bg-accent px-4 py-2.5 text-base font-semibold text-white transition-colors duration-150 hover:bg-accent-hover"
        >
          <FaGithub className="text-xl" />
          Sign in with GitHub
        </a>

        <p className="mt-6 text-center text-xs text-text-muted">
          By signing in you agree to the terms of service
        </p>
      </motion.div>
    </div>
  );
};

export default LoginPage;

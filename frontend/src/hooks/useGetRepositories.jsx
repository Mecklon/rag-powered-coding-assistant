import { useEffect } from "react";
import useGetFetch from "./useGetFetch";

const useGetRepositories = () => {
  const { state: repos, setState, error, loading, fetch } = useGetFetch(null);

  useEffect(() => {
    fetch("/api/github/repos");
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return { repos, error, loading };
};

export default useGetRepositories;
import { useCallback } from "react";
import { useDispatch } from "react-redux";
import api from "../api/api";
import { logout } from "../store/slices/UserSlice";

/**
 * Clears the http-only cookie on the backend and resets the user in Redux.
 */
const useLogout = () => {
  const dispatch = useDispatch();

  const handleLogout = useCallback(async () => {
    try {
      await api.post("/api/auth/logout");
    } catch (error) {
      console.error("Logout failed", error);
    } finally {
      dispatch(logout());
    }
  }, [dispatch]);

  return handleLogout;
};

export default useLogout;

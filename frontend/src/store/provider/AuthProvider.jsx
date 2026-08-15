import { useEffect } from "react";
import { useDispatch } from "react-redux";
import { fetchCurrentUser } from "../slices/UserSlice";

/**
 * Wraps the app and, on first mount, checks for the http-only cookie via the
 * autologin endpoint. Dispatches the fetched user into the Redux slice so it is
 * available app-wide through useSelector.
 */
const AuthProvider = ({ children }) => {
  const dispatch = useDispatch();

  useEffect(() => {
    dispatch(fetchCurrentUser());
  }, [dispatch]);

  return children;
};

export default AuthProvider;

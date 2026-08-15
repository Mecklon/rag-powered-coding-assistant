import { useEffect, useRef, useState } from "react";
import api from "../api/api";
import { useDispatch } from "react-redux";
import { addErrorWithTimeout } from "../../store/ErrorSlice";

const useGetFetch = (initialValue = null) => {
  const [state, setState] = useState(initialValue);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  const dispatch = useDispatch();
  const controllerRef = useRef();

  const fetch = async (query, skipAuth = false, headers = {}) => {
    if (controllerRef.current) controllerRef.current.abort();
    controllerRef.current = new AbortController();

    setError(null);
    setLoading(true);
    try {
      const res = await api.get(query, {
        skipAuth: skipAuth,
        signal: controllerRef.current.signal,
        headers,
      });
      setState(res.data);
      return res.data;
    } catch (error) {
      if(error.response){
        console.log("server error")
        console.log(error.response)
        const errorInfo = {
        type:"ERROR",
        httpStatus: error.response.status,
        exceptionType: error.response.data?.code,
        message: error.response.data?.message
        };
        setError(error.response.data)
        dispatch(addErrorWithTimeout(errorInfo));
      }else if(error.request){
        console.log("Network error")
        const errorInfo = {
        type:"ERROR",
        httpStatus: null,
        exceptionType: "NETWORK_ERROR",
        message: "Unable to reach server"
        };
        setError("Network error")
        dispatch(addErrorWithTimeout(errorInfo));
      }else{
        console.log("something went wrong")
        const errorInfo = {
        type:"ERROR",
        httpStatus: null,
        exceptionType: "UNKNOWN_ERROR",
        message: error.message
        };
        setError("Something went wrong")
        dispatch(addErrorWithTimeout(errorInfo));
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    return () => {
      if (controllerRef.current) controllerRef.current.abort();
    };
  }, []);

  return { state, setState, error, loading, fetch };
};
export default useGetFetch;

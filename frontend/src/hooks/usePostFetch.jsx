import { useState, useRef,useEffect } from "react"
import api from "../api/api"
import { useDispatch } from "react-redux";
import { addErrorWithTimeout } from "../store/slices/ErrorSlice";


const usePostFetch = (initialValue=null)=>{
    const [state,setState] = useState(initialValue)
    const [error, setError] = useState(null)
    const [loading, setLoading] = useState(false)


    const dispatch = useDispatch();
    const controllerRef = useRef()

    const fetch = async(query, body, skipAuth = false, headers = {})=>{
        if(controllerRef.current) controllerRef.current.abort();
        controllerRef.current = new AbortController()

        setLoading(true)
        setError(null)

        try{
            const res = await api.post(query,body, {skipAuth: skipAuth, signal: controllerRef.current.signal, headers})
            setState(res.data)
            return res.data
        }catch(error){
            // Ignore aborted/cancelled requests (e.g. StrictMode double-invoke
            // or a newer fetch superseding this one). These are not real errors.
            if (error.code === "ERR_CANCELED" || error.name === "CanceledError") {
                return;
            }
            if(error.response){
                console.log("server error")
                console.log(error.response)
                const errorInfo = {
                type:"ERROR",
                httpStatus: error.response.status,
                exceptionType: error.response.data?.code,
                message: error.response.data?.message
                };
                console.log(errorInfo)
                setError(error.response.data)
                console.log("dispatching error")
                dispatch(addErrorWithTimeout(errorInfo));
                console.log("error dispatched")
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
        }finally{
            setLoading(false)
        }
    }


    useEffect(()=>{
        return ()=>{
            if(controllerRef.current)controllerRef.current.abort()
        }
    },[])

    return {state,setState, error, loading, fetch};
}

export default usePostFetch
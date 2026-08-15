import { useContext } from "react";
import { websocketContext } from "../WebSocketProvider.jsx";

const useWebSocketContext = ()=>{
    return useContext(websocketContext)
}

export default useWebSocketContext
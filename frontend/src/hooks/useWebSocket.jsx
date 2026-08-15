import { useMemo, useEffect } from "react";
import { Client } from "@stomp/stompjs";

let brokerURL;

if (import.meta.env.VITE_USE_RENDER === "true") {
  brokerURL = import.meta.env.VITE_RENDER_WS_URL;
} else if (import.meta.env.VITE_USE_NGINX === "true") {
  brokerURL = `${window.location.protocol === "https:" ? "wss" : "ws"}://${window.location.host}/ws`;
} else {
  // Local development
  brokerURL = "ws://localhost:9090/ws";
}
  
export function useWebSocket(token) {


  const client = useMemo(() => {

    return new Client({
      brokerURL,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      reconnectDelay: 5000,
    });
  }, [token, brokerURL]);

  useEffect(() => {
    client.activate();
    console.log(brokerURL)
    return () => {
      client.deactivate();
    };
  }, [client]);

  return client;
}
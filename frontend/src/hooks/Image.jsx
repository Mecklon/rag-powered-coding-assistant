import { useEffect, useState } from "react";
import api from "../api/api";

const Image = ({
  path = null,
  className = "",
  fallback = null,
  fullToggle = false,
  onLoadCallBack,
}) => {
  const [src, setSrc] = useState(null);
  const [loading, setLoading] = useState(false);
  useEffect(() => {
    if (path === null) return;

    let objectUrl;

    const getData = async () => {
      try {
        setLoading(true);

        const fileUrl =
          import.meta.env.VITE_USE_RENDER === "true"
            ? `${import.meta.env.VITE_RENDER_API_URL}/files/${path}`
            : import.meta.env.VITE_USE_NGINX === "true"
              ? `${window.location.protocol}//${window.location.host}/api/files/${path}`
              : `http://localhost:9090/files/${path}`;

        const res = await api.get(fileUrl, {
          responseType: "blob",
        });
        objectUrl = URL.createObjectURL(res.data);
        setSrc(objectUrl);
      } catch (err) {
        console.log("image fetch err: ", err);
      } finally {
        setLoading(false);
      }
    };

    getData();
    return () => {
      if (objectUrl) {
        URL.revokeObjectURL(objectUrl);
      }
    };
  }, [path]);

  if (loading) {
    return <img src={fallback} className={className} alt="" />;
  }
  return (
    <img
      onLoad={() => {
        if (onLoadCallBack) {
          onLoadCallBack();
        }
      }}
      src={src || fallback}
      className={className}
      alt=""
    />
  );
};
export default Image;

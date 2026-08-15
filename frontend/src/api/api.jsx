import axios from "axios";

// const baseURL =
//   import.meta.env.VITE_USE_RENDER === "true"
//     ? import.meta.env.VITE_RENDER_API_URL
//     : import.meta.env.VITE_API_URL;




//     console.log(import.meta.env.VITE_USE_RENDER);
// console.log(import.meta.env.VITE_RENDER_API_URL);
// console.log(baseURL);
 

const api = axios.create({
  baseURL:"http://localhost:9090/",
  withCredentials: true,
});

//aafsd

//change

export default api;

import { configureStore } from "@reduxjs/toolkit";
import userReducer from "./slices/UserSlice";
import errorReducer from "./slices/ErrorSlice";

const store = configureStore({
  reducer: {
    user: userReducer,
    errors: errorReducer,
  },
});

export default store;

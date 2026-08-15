import { createSlice } from "@reduxjs/toolkit";

const initialState = {
  errors: [],
};

const errorSlice = createSlice({
  name: "errors",
  initialState,
  reducers: {
    addError: (state, action) => {
      state.errors.push(action.payload);
    },
    removeError: (state, action) => {
      state.errors = state.errors.filter((e) => e.id !== action.payload);
    },
  },
});

export const { addError, removeError } = errorSlice.actions;

/**
 * Adds an error and schedules its removal after a timeout.
 */
export const addErrorWithTimeout =
  (error, duration = 5000) =>
  (dispatch) => {
    const id = Date.now() + Math.random();
    dispatch(addError({ ...error, id }));
    setTimeout(() => dispatch(removeError(id)), duration);
  };

export default errorSlice.reducer;
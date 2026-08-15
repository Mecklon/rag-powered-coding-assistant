import { createAsyncThunk, createSlice } from "@reduxjs/toolkit";
import api from "../../api/api";

const initialState = {
  user: null,
  loading: false,
  error: null,
  initialized: false,
};

/**
 * Called once on app load. Hits the autologin route. The http-only cookie is
 * sent automatically; no token is stored client-side.
 */
export const fetchCurrentUser = createAsyncThunk(
  "user/fetchCurrentUser",
  async (_, { rejectWithValue }) => {
    try {
      const res = await api.get("/api/auth/autologin");
      return res.data;
    } catch (error) {
      if (error.response?.status === 401) {
        return null;
      }
      return rejectWithValue(error.response?.data?.message || "Failed to load user");
    }
  }
);

const userSlice = createSlice({
  name: "user",
  initialState,
  reducers: {
    setUser: (state, action) => {
      state.user = action.payload;
      state.initialized = true;
    },
    logout: (state) => {
      state.user = null;
    },
    clearError: (state) => {
      state.error = null;
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(fetchCurrentUser.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(fetchCurrentUser.fulfilled, (state, action) => {
        state.loading = false;
        state.user = action.payload;
        state.initialized = true;
      })
      .addCase(fetchCurrentUser.rejected, (state, action) => {
        state.loading = false;
        state.user = null;
        state.error = action.payload;
        state.initialized = true;
      });
  },
});

export const { setUser, logout, clearError } = userSlice.actions;
export default userSlice.reducer;

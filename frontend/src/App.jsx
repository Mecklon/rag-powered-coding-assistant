import { useSelector } from 'react-redux'
import LoginPage from './pages/LoginPage'
import HomePage from './pages/HomePage'
import './App.css'

function App() {
  const { user, loading, initialized } = useSelector((state) => state.user)

  // While the initial autologin check is in flight, show a loading screen.
  if (loading && !initialized) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-bg">
        <div className="flex flex-col items-center gap-3">
          <div className="h-8 w-8 animate-spin rounded-full border-2 border-accent border-t-transparent" />
          <p className="text-sm text-text-secondary">Loading...</p>
        </div>
      </div>
    )
  }

  return user ? <HomePage /> : <LoginPage />
}

export default App

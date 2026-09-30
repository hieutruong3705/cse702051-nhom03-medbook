import { reactive, computed } from 'vue'

const state = reactive({
  isAuthenticated: !!localStorage.getItem('token'),
  user: JSON.parse(localStorage.getItem('user') || '{}')
})

export function useAuth() {
  const dashboardRoute = computed(() => {
    if (!state.isAuthenticated || !state.user.roles) return '/'
    const roles = state.user.roles
    if (roles.includes('ADMIN') || roles.includes('ROLE_ADMIN')) return '/admin/dashboard'
    if (roles.includes('DOCTOR') || roles.includes('ROLE_DOCTOR')) return '/doctor/dashboard'
    return '/patient/dashboard'
  })

  const login = (token, user) => {
    localStorage.setItem('token', token)
    localStorage.setItem('user', JSON.stringify(user))
    state.isAuthenticated = true
    state.user = user
  }

  const logout = () => {
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    state.isAuthenticated = false
    state.user = {}
  }

  return {
    isAuthenticated: computed(() => state.isAuthenticated),
    user: computed(() => state.user),
    dashboardRoute,
    login,
    logout
  }
}

import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuth } from '../composables/useAuth'

import Home from '../views/Home.vue'
import Login from '../views/Login.vue'
import PatientDashboard from '../views/PatientDashboard.vue'

import DoctorDashboard from '../views/DoctorDashboard.vue'
import AdminDashboard from '../views/AdminDashboard.vue'

const routes = [
  { path: '/', component: Home },
  { path: '/login', component: Login },
  { path: '/patient/dashboard', component: PatientDashboard, meta: { requiresAuth: true } },
  { path: '/doctor/dashboard', component: DoctorDashboard, meta: { requiresAuth: true } },
  { path: '/admin/dashboard', component: AdminDashboard, meta: { requiresAuth: true } }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const { isAuthenticated } = useAuth()
  if (to.meta.requiresAuth && !isAuthenticated.value) {
    next('/login')
  } else {
    next()
  }
})

export default router

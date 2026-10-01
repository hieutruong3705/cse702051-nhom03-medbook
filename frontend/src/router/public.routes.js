// Route con của khu công khai (Dev 5). Đường dẫn tương đối so với `/`.
// Các view dưới đây TỰ bọc `PublicLayout`/`DashboardLayout` trong template nên khai báo trực tiếp.
// Còn thiếu (FE-01): chi tiết bác sĩ `doctors/:id`.
export default [
  { path: '', name: 'home', component: () => import('@/views/Home.vue'), meta: { title: 'Trang chủ' } },

  // --- Đăng nhập / đăng ký / khôi phục mật khẩu ---
  {
    path: 'login',
    name: 'login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { guestOnly: true, title: 'Đăng nhập' }
  },
  {
    path: 'register',
    name: 'register',
    component: () => import('@/views/auth/RegisterView.vue'),
    meta: { guestOnly: true, title: 'Đăng ký' }
  },
  {
    path: 'forgot-password',
    name: 'forgot-password',
    component: () => import('@/views/auth/ForgotPasswordView.vue'),
    meta: { guestOnly: true, title: 'Quên mật khẩu' }
  },
  {
    // Không đặt guestOnly: người đang đăng nhập bấm liên kết trong email vẫn phải đặt lại được.
    path: 'reset-password',
    name: 'reset-password',
    component: () => import('@/views/auth/ResetPasswordView.vue'),
    meta: { title: 'Đặt lại mật khẩu' }
  },

  // --- Thông tin công khai ---
  {
    path: 'doctors',
    name: 'doctors',
    component: () => import('@/views/public/DoctorsView.vue'),
    meta: { title: 'Bác sĩ' }
  },
  {
    path: 'specialties',
    name: 'specialties',
    component: () => import('@/views/public/SpecialtiesView.vue'),
    meta: { title: 'Chuyên khoa' }
  },
  {
    path: 'services',
    name: 'services',
    component: () => import('@/views/public/ServicesView.vue'),
    meta: { title: 'Dịch vụ' }
  },

  // --- Tài khoản (mọi vai trò đã đăng nhập) ---
  {
    path: 'account/profile',
    name: 'account-profile',
    component: () => import('@/views/account/ProfileView.vue'),
    meta: { requiresAuth: true, title: 'Hồ sơ tài khoản' }
  },
  {
    path: 'account/change-password',
    name: 'account-change-password',
    component: () => import('@/views/account/ChangePasswordView.vue'),
    meta: { requiresAuth: true, title: 'Đổi mật khẩu' }
  }
]

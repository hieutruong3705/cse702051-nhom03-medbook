// Route con của khu công khai (Dev 5). Đường dẫn tương đối so với `/`.
// Các view dưới đây TỰ bọc `PublicLayout`/`DashboardLayout` trong template nên khai báo trực tiếp.
// Trang chi tiết (`doctors/:id`, `specialties/:id`) tự cập nhật tiêu đề và mô tả theo dữ liệu sau khi tải.
export default [
  {
    path: '',
    name: 'home',
    component: () => import('@/views/Home.vue'),
    meta: {
      title: 'Trang chủ',
      description: 'MedBook: tìm bác sĩ theo chuyên khoa và đặt lịch khám trực tuyến tại phòng khám.'
    }
  },

  // --- Đăng nhập / đăng ký / khôi phục mật khẩu ---
  {
    path: 'login',
    name: 'login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: {
      guestOnly: true,
      title: 'Đăng nhập',
      description: 'Đăng nhập MedBook để đặt lịch khám, xem lịch hẹn, kết quả khám, đơn thuốc và hóa đơn của bạn.'
    }
  },
  {
    path: 'register',
    name: 'register',
    component: () => import('@/views/auth/RegisterView.vue'),
    meta: {
      guestOnly: true,
      title: 'Đăng ký',
      description: 'Tạo tài khoản bệnh nhân MedBook để đặt lịch khám trực tuyến và theo dõi hồ sơ khám bệnh của bạn.'
    }
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
    meta: {
      title: 'Bác sĩ',
      description: 'Danh sách bác sĩ của phòng khám MedBook: tìm theo tên hoặc chuyên khoa rồi đặt lịch khám với bác sĩ bạn chọn.'
    }
  },
  {
    path: 'doctors/:id(\\d+)',
    name: 'doctor-detail',
    component: () => import('@/views/public/DoctorDetailView.vue'),
    meta: {
      title: 'Chi tiết bác sĩ',
      description: 'Thông tin bác sĩ của phòng khám MedBook: giới thiệu, kinh nghiệm, đánh giá của bệnh nhân và giờ khám còn trống.'
    }
  },
  {
    path: 'specialties',
    name: 'specialties',
    component: () => import('@/views/public/SpecialtiesView.vue'),
    meta: {
      title: 'Chuyên khoa',
      description: 'Danh sách các chuyên khoa đang nhận khám tại phòng khám MedBook.'
    }
  },
  {
    path: 'specialties/:id(\\d+)',
    name: 'specialty-detail',
    component: () => import('@/views/public/SpecialtyDetailView.vue'),
    meta: {
      title: 'Chi tiết chuyên khoa',
      description: 'Thông tin chuyên khoa tại phòng khám MedBook: bác sĩ đang nhận khám và đánh giá của bệnh nhân.'
    }
  },
  {
    path: 'services',
    name: 'services',
    component: () => import('@/views/public/ServicesView.vue'),
    meta: {
      title: 'Dịch vụ',
      description: 'Bảng dịch vụ khám của MedBook: thời lượng và giá của từng dịch vụ.'
    }
  },

  {
    path: 'about',
    name: 'about',
    component: () => import('@/views/public/ClinicAboutView.vue'),
    meta: {
      title: 'Giới thiệu phòng khám',
      description: 'Giới thiệu phòng khám đa khoa MedBook: địa chỉ, giờ làm việc, số bác sĩ, chuyên khoa và đánh giá của người bệnh.'
    }
  },
  {
    path: 'reviews',
    name: 'reviews',
    component: () => import('@/views/public/ReviewsView.vue'),
    meta: {
      title: 'Đánh giá khám chữa bệnh',
      description: 'Đánh giá của người bệnh sau khi khám tại MedBook, lọc theo chuyên khoa, bác sĩ và số sao.'
    }
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

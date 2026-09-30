document.addEventListener('DOMContentLoaded', () => {
    const loginCard = document.getElementById('login-card');
    const registerCard = document.getElementById('register-card');
    const dashboardCard = document.getElementById('dashboard-card');
    
    const showRegisterBtn = document.getElementById('show-register');
    const showLoginBtn = document.getElementById('show-login');
    const messageBox = document.getElementById('message-box');

    // Nút chuyên sang Form Đăng ký
    showRegisterBtn.addEventListener('click', (e) => {
        e.preventDefault();
        loginCard.classList.add('hidden');
        registerCard.classList.remove('hidden');
        hideMessage();
    });

    // Nút quay lại Form Đăng nhập
    showLoginBtn.addEventListener('click', (e) => {
        e.preventDefault();
        registerCard.classList.add('hidden');
        loginCard.classList.remove('hidden');
        hideMessage();
    });

    // Xử lý Gửi thông tin ĐĂNG KÝ
    document.getElementById('register-form').addEventListener('submit', async (e) => {
        e.preventDefault();
        
        const fullName = document.getElementById('reg-fullname').value;
        const username = document.getElementById('reg-username').value;
        const email = document.getElementById('reg-email').value;
        const password = document.getElementById('reg-password').value;
        const role = document.getElementById('reg-role').value;

        try {
            const response = await fetch('/api/register', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ fullName, username, email, password, role })
            });

            const data = await response.json();

            if (response.ok) {
                showMessage('Đăng ký thành công! Đang chuyển sang trang Đăng nhập...', 'success');
                // Tự động chuyển về trang Đăng nhập sau 1.5 giây
                setTimeout(() => {
                    registerCard.classList.add('hidden');
                    loginCard.classList.remove('hidden');
                    document.getElementById('login-username').value = username;
                    document.getElementById('register-form').reset();
                    showMessage('Vui lòng nhập mật khẩu để đăng nhập.', 'success');
                }, 1500);
            } else {
                showMessage(data.message || 'Đăng ký thất bại!', 'error');
            }
        } catch (err) {
            showMessage('Không thể kết nối đến máy chủ!', 'error');
        }
    });

    // Xử lý Gửi thông tin ĐĂNG NHẬP
    document.getElementById('login-form').addEventListener('submit', async (e) => {
        e.preventDefault();
        
        const username = document.getElementById('login-username').value;
        const password = document.getElementById('login-password').value;

        try {
            const response = await fetch('/api/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password })
            });

            const data = await response.json();

            if (response.ok) {
                loginCard.classList.add('hidden');
                dashboardCard.classList.remove('hidden');
                document.getElementById('user-display-name').innerText = data.user.fullName || data.user.username;
                document.getElementById('user-display-role').innerText = data.user.role || 'Bệnh nhân';
                hideMessage();
            } else {
                showMessage(data.message || 'Sai tài khoản hoặc mật khẩu!', 'error');
            }
        } catch (err) {
            showMessage('Không thể kết nối đến máy chủ!', 'error');
        }
    });

    // Đăng xuất
    document.getElementById('logout-btn').addEventListener('click', () => {
        dashboardCard.classList.add('hidden');
        loginCard.classList.remove('hidden');
        document.getElementById('login-form').reset();
        showMessage('Đã đăng xuất thành công.', 'success');
    });

    function showMessage(msg, type) {
        messageBox.innerText = msg;
        messageBox.className = `alert alert-${type}`;
        messageBox.classList.remove('hidden');
    }

    function hideMessage() {
        messageBox.classList.add('hidden');
    }
});

// Dùng đường dẫn tương đối (relative path) để tự động nhận domain khi deploy
const API_URL = '';

const registerForm = document.getElementById('registerForm');
const loginForm = document.getElementById('loginForm');
const messageDiv = document.getElementById('message');

function showMessage(text, isSuccess) {
    messageDiv.innerText = text;
    messageDiv.className = `message ${isSuccess ? 'success' : 'error'}`;
}

// Xử lý Đăng Ký
registerForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const fullName = document.getElementById('regFullName').value;
    const username = document.getElementById('regUsername').value;
    const email = document.getElementById('regEmail').value;
    const password = document.getElementById('regPassword').value;

    try {
        const res = await fetch(`${API_URL}/v1/auth/register`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ fullName, username, email, password })
        });
        const data = await res.json();
        if (res.ok) {
            showMessage(data.message, true);
            registerForm.reset();
        } else {
            showMessage(data.message || 'Đăng ký thất bại', false);
        }
    } catch (err) {
        showMessage('Không thể kết nối đến máy chủ', false);
    }
});

// Xử lý Đăng Nhập
loginForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const username = document.getElementById('loginUsername').value;
    const password = document.getElementById('loginPassword').value;

    try {
        const res = await fetch(`${API_URL}/v1/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });
        const data = await res.json();
        if (res.ok) {
            showMessage('Đăng nhập thành công! Token: ' + data.token.substring(0, 15) + '...', true);
            loginForm.reset();
        } else {
            showMessage(data.message || 'Đăng nhập thất bại', false);
        }
    } catch (err) {
        showMessage('Không thể kết nối đến máy chủ', false);
    }
});

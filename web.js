const express = require('express');
const helmet = require('helmet');
const cors = require('cors');

const app = express();
const PORT = process.env.PORT || 3000;

// 1. Cấu hình bảo mật cơ bản (Helmet)
// Sử dụng helmet nhưng cấu hình cho phép tải tài nguyên hình ảnh/script an toàn
app.use(helmet({
    contentSecurityPolicy: false, 
}));

// 2. Kích hoạt CORS để cho phép Frontend kết nối
app.use(cors());

// Đọc dữ liệu định dạng JSON từ client gửi lên
app.use(express.json());

// --- CÁC ROUTE CỦA DỰ ÁN ---
app.get('/', (req, res) => {
    res.json({
        status: "success",
        message: "Hệ thống Medbook đã được bảo mật và hoạt động thành công!",
        version: "V5.0"
    });
});

// Route giả lập lỗi để kiểm tra tính năng ẩn lỗi production
app.get('/error-test', (req, res, next) => {
    const err = new Error("Lỗi mẫu để kiểm tra hệ thống!");
    err.status = 500;
    next(err);
});

// 3. MIDDLEWARE TẮT HIỂN THỊ LỖI (Error Handler)
// Đặt ở cuối cùng trước khi app.listen để bắt toàn bộ lỗi phát sinh
app.use((err, req, res, next) => {
    console.error("Chi tiết lỗi ngầm trên Server:", err.stack); // Chỉ ghi log cho lập trình viên xem
    
    const statusCode = err.status || 500;
    res.status(statusCode).json({
        success: false,
        // Nếu chạy ở môi trường production sẽ ẩn chi tiết lỗi đi, trả về thông báo chung
        message: process.env.NODE_ENV === 'production' 
            ? "Đã xảy ra lỗi hệ thống, vui lòng thử lại sau!" 
            : err.message 
    });
});

// Khởi động server
app.listen(PORT, () => {
    console.log(`Server đang chạy ổn định tại cổng ${PORT}`);
});

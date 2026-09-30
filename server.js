const express = require('express');
const path = require('path');
const { Pool } = require('pg');
const bcrypt = require('bcrypt');
const jwt = require('jsonwebtoken');

const app = express();
const PORT = process.env.PORT || 3000;

const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  ssl: process.env.NODE_ENV === 'production' ? { rejectUnauthorized: false } : false
});

app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

app.post('/v1/auth/register', async (req, res) => {
  const { fullName, username, email, password } = req.body;
  try {
    const userExist = await pool.query('SELECT * FROM users WHERE username = $1 OR email = $2', [username, email]);
    if (userExist.rows.length > 0) {
      return res.status(400).json({ message: 'Tên đăng nhập hoặc Email đã tồn tại!' });
    }

    const hashedPassword = await bcrypt.hash(password, 10);
    const newUser = await pool.query(
      'INSERT INTO users (full_name, username, email, password_hash) VALUES ($1, $2, $3, $4) RETURNING id, username, email',
      [fullName, username, email, hashedPassword]
    );

    return res.status(201).json({ message: 'Đăng ký tài khoản thành công!', user: newUser.rows[0] });
  } catch (error) {
    console.error(error);
    return res.status(500).json({ message: 'Lỗi máy chủ khi đăng ký.' });
  }
});

app.post('/v1/auth/login', async (req, res) => {
  const { username, password } = req.body;
  try {
    const result = await pool.query('SELECT * FROM users WHERE username = $1', [username]);
    if (result.rows.length === 0) {
      return res.status(401).json({ message: 'Tài khoản hoặc mật khẩu không chính xác.' });
    }

    const user = result.rows[0];
    const isMatch = await bcrypt.compare(password, user.password_hash);
    if (!isMatch) {
      return res.status(401).json({ message: 'Tài khoản hoặc mật khẩu không chính xác.' });
    }

    const token = jwt.sign(
      { userId: user.id, username: user.username },
      process.env.JWT_SECRET || 'medbook_secret_key',
      { expiresIn: '24h' }
    );

    return res.json({ message: 'Đăng nhập thành công!', token });
  } catch (error) {
    console.error(error);
    return res.status(500).json({ message: 'Lỗi máy chủ khi đăng nhập.' });
  }
});

app.get('/', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

app.listen(PORT, () => {
  console.log(`Server MedBook thật đang chạy tại cổng ${PORT}`);
});
# MedBook frontend

Vue 3 + Vite + Tailwind 4. API base URL được đọc từ `VITE_API_BASE_URL`
(mặc định `/api/v1` trong `.env.development` và `.env.production`).

```powershell
npm install
npm run dev
npm run build
npm run test
```

Dev server proxy mọi request `/api` tới `http://localhost:8080`, nên frontend gọi
backend thật mà không cần hardcode host. Build xuất thẳng vào
`../src/main/resources/static` và dùng hash history để Spring Boot có thể phục vụ
SPA tĩnh.

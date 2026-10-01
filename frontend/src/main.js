import { createApp } from 'vue'
import { createPinia } from 'pinia'
import '@fortawesome/fontawesome-free/css/all.min.css'
import './style.css'
import App from './App.vue'
import router from './router'
import { enableMocks } from './mocks'

await enableMocks()

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')

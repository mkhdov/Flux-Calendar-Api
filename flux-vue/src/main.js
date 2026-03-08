import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'

import 'bootstrap/dist/css/bootstrap.min.css'
import 'bootstrap'
import 'bootstrap-icons/font/bootstrap-icons.css'
import './assets/custom.scss' // Custom overrides

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')

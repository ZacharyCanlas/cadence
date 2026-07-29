import { createApp } from 'vue';
import { registerSW } from 'virtual:pwa-register';
import App from './App.vue';
import './styles.css';

// Offline install is a bonus, not a requirement for the app to run; the
// register call swallows its own failures.
registerSW({ immediate: true });

createApp(App).mount('#app');

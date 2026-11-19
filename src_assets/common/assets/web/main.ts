import { createPinia } from 'pinia';
import { createApp } from 'vue';

import App from '@/App.vue';
import { createVibepolloI18n } from '@/i18n';
import router from '@/router';
import '@/styles/tokens.css';
import '@/styles/base.css';
import '@/styles/components.css';
import '@/styles/shell.css';
import '@/styles/views.css';

// WebView can focus its first link on activation; reveal skip navigation only
// after explicit keyboard/controller navigation, never on window activation.
document.addEventListener('keydown', (event) => {
  if (['Tab', 'ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(event.key)) {
    document.documentElement.classList.add('keyboard-navigation');
  }
}, true);
document.addEventListener('pointerdown', () => {
  document.documentElement.classList.remove('keyboard-navigation');
}, true);

const i18n = await createVibepolloI18n();

router.afterEach((route) => {
  const key = typeof route.meta.titleKey === 'string' ? route.meta.titleKey : 'ui.nav.overview';
  const title = i18n.global.t(key);
  document.title = route.path === '/' ? 'NOUS' : `${title} · NOUS`;
});

const app = createApp(App).use(createPinia()).use(router).use(i18n);
// Do not block module evaluation: lazy route chunks import shared exports from this entry.
void router.isReady().then(() => app.mount('#app'));

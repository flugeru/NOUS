<script setup lang="ts">
import { computed, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { apiPost } from '@/api/client';
defineProps<{ manage?: boolean }>();
const preview = import.meta.env.VITE_NOUS_LOCAL_ACCOUNT_TEST === '1' && location.origin === 'http://127.0.0.1:48174';
const localHost = location.protocol === 'https:' && location.hostname === '127.0.0.1';
const broker = 'http://127.0.0.1:48173/';
const { locale } = useI18n();
const pt = computed(() => locale.value.startsWith('pt'));
const ticket = ref('');
const busy = ref(false);
const note = ref('');
async function access(action: 'start' | 'disable') {
  busy.value = true; ticket.value = ''; note.value = '';
  try {
    const result = await apiPost<{ status: boolean; ticket?: string }>('/api/account-access', { action });
    if (!result.status) throw Error();
    if (action === 'start' && /^[A-F0-9]{64}$/.test(result.ticket ?? '')) ticket.value = result.ticket!;
    else if (action === 'disable') note.value = pt.value ? 'Novos vínculos bloqueados. Revogue dispositivos existentes em Dispositivos.' : 'New links blocked. Revoke existing devices in Devices.';
    else throw Error();
  } catch { note.value = pt.value ? 'Acesso por conta indisponível.' : 'Account access unavailable.'; }
  finally { busy.value = false; }
}
</script>
<template>
  <section :class="manage ? 'vs-page vs-stack account-page' : 'vs-stack account-login'" aria-labelledby="account-title">
    <header v-if="manage" class="vs-stack account-heading">
      <h1 id="account-title">{{ pt ? 'Conta NOUS' : 'NOUS account' }}</h1>
      <p>{{ pt ? 'Encontre este PC nos seus dispositivos e escolha como autorizar novas conexões.' : 'Find this PC on your devices and choose how to authorize new connections.' }}</p>
    </header>
    <div v-if="preview || localHost" :class="manage ? 'vs-card vs-stack' : 'vs-stack'">
      <h2 v-if="manage">{{ pt ? 'Conectar com Google' : 'Connect with Google' }}</h2>
      <p>{{ pt ? 'Login opcional, aberto no navegador. Sua conta não substitui a senha administrativa deste PC.' : 'Optional sign-in opens in your browser. Your account does not replace this PC’s administrator password.' }}</p>
      <div class="vs-cluster">
        <a class="vs-button vs-button--primary" :href="broker" target="_blank" rel="noopener noreferrer">{{ pt ? 'Entrar com Google' : 'Sign in with Google' }}</a>
      </div>
    </div>
    <p v-else class="vs-card">{{ pt ? 'Gerencie a conta na janela NOUS deste PC. O pareamento local continua disponível em Dispositivos.' : 'Manage the account in this PC’s NOUS window. Local pairing remains available in Devices.' }}</p>
    <section v-if="localHost && manage" class="vs-card vs-stack" aria-labelledby="account-access-title">
      <h2 id="account-access-title">{{ pt ? 'Acesso por conta' : 'Account access' }}</h2>
      <p>{{ pt ? 'Ative para vincular instalações da mesma conta. Bloquear novos vínculos não remove dispositivos já autorizados.' : 'Enable linking installations from the same account. Blocking new links does not remove devices already authorized.' }}</p>
      <div class="vs-cluster">
        <button type="button" class="vs-button vs-button--secondary" :disabled="busy" @click="access('start')">{{ pt ? 'Ativar acesso por conta' : 'Enable account access' }}</button>
        <button type="button" class="vs-button vs-button--secondary" :disabled="busy" @click="access('disable')">{{ pt ? 'Bloquear novos vínculos' : 'Block new links' }}</button>
      </div>
      <div v-if="ticket" class="vs-cluster">
        <a class="vs-button vs-button--primary" :href="`${broker}link#ticket=${ticket}`" target="_blank" rel="noopener noreferrer">{{ pt ? 'Continuar no navegador' : 'Continue in browser' }}</a>
      </div>
      <p v-if="note" role="status">{{ note }}</p>
      <RouterLink to="/devices">{{ pt ? 'Gerenciar dispositivos autorizados' : 'Manage authorized devices' }}</RouterLink>
    </section>
  </section>
</template>

<style scoped>
.account-page { max-inline-size: 64rem; gap: var(--vs-space-24); }
.account-heading { gap: var(--vs-space-8); }
.account-page p { color: var(--vs-color-text-secondary); max-inline-size: 65ch; }
.account-page h2 { font-size: 1.125rem; }
.account-login { padding-block-start: var(--vs-space-16); }
.account-login p { color: var(--vs-color-text-secondary); font-size: .875rem; }
</style>

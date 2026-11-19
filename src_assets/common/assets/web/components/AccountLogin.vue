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
    else if (action === 'disable') note.value = pt.value ? 'Novos vínculos bloqueados. Revogue dispositivos existentes na lista abaixo.' : 'New links blocked. Revoke existing devices in the list below.';
    else throw Error();
  } catch { note.value = pt.value ? 'Acesso por conta indisponível.' : 'Account access unavailable.'; }
  finally { busy.value = false; }
}
</script>
<template>
  <section v-if="preview || localHost" aria-label="NOUS">
    <p>{{ pt ? 'Conta NOUS · separada da senha administrativa do PC.' : 'NOUS account · separate from the PC administrator password.' }}</p>
    <a class="vs-button vs-button--primary" :href="broker" target="_blank" rel="noopener noreferrer">{{ pt ? 'Entrar com Google' : 'Sign in with Google' }}</a>
    <template v-if="localHost && manage">
      <button type="button" class="vs-button" :disabled="busy" @click="access('start')">{{ pt ? 'Ativar acesso por conta' : 'Enable account access' }}</button>
      <button type="button" class="vs-button" :disabled="busy" @click="access('disable')">{{ pt ? 'Bloquear novos vínculos' : 'Block new links' }}</button>
      <a v-if="ticket" class="vs-button" :href="`${broker}link#ticket=${ticket}`" target="_blank" rel="noopener noreferrer">{{ pt ? 'Continuar no navegador' : 'Continue in browser' }}</a>
      <p v-if="note" role="status">{{ note }}</p>
    </template>
  </section>
</template>

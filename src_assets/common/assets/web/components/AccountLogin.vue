<script setup lang="ts">
import { useI18n } from 'vue-i18n';
// Test broker runs on this PC only. Never send remote panel visitors to their own loopback.
const enabled = import.meta.env.VITE_NOUS_LOCAL_ACCOUNT_TEST === '1' && ['127.0.0.1', 'localhost'].includes(location.hostname);
const { locale } = useI18n();
</script>
<template>
  <section v-if="enabled" aria-label="NOUS account">
    <p>{{ locale.startsWith('pt') ? 'Conta NOUS · separada da senha administrativa do PC.' : 'NOUS account · separate from the PC administrator password.' }}</p>
    <a class="vs-button vs-button--primary" href="http://127.0.0.1:48173/" target="_blank" rel="noopener noreferrer">{{ locale.startsWith('pt') ? 'Entrar com Google' : 'Sign in with Google' }}</a>
  </section>
</template>

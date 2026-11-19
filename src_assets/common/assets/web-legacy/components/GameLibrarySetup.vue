<script setup lang="ts">
import { providerSupported } from '../../web/utils/providerCapabilities';
import { computed, nextTick, onBeforeUnmount, reactive, ref, useId, watch } from 'vue';
import { useI18n } from 'vue-i18n';

// Shared by classic and NOUS: local messages avoid depending on either app's catalog layout.
const { t } = useI18n({
  messages: {
    en: {
      title: 'Setup Game Library Integration',
      configured: 'Library manager settings',
      intro: 'Step {step} of 2 · {task}. You can change these settings later.',
      choose: 'Choose libraries',
      configure: 'Configure synchronization',
      loading: 'Loading your libraries…',
      libraries: 'Libraries to sync automatically',
      recommended: 'Recommended on Windows',
      settings: 'Library sync settings',
      none: 'Automatic sync is off for all libraries.',
      unavailable: 'No supported libraries are available on this host.',
      all: 'Sync all installed games',
      recent: 'Recent games',
      age: 'Maximum age in days (0 = no limit)',
      remove: 'Remove uninstalled games from automatic sync',
      tools: 'Include Steam tools',
      lutrisSteam: 'Include Steam games from Lutris',
      back: 'Back',
      done: 'Done',
      cancel: 'Cancel',
      next: 'Next: Library settings',
      save: 'Save settings',
      saving: 'Saving and syncing…',
      retry: 'Try again',
      loadError: 'Unable to load game library settings. Try again before making changes.',
      installError: 'Unable to install the Playnite extension. Install Playnite, then try again.',
      saveError: 'Unable to save game library settings. Your draft is still available.',
      invalid: 'Enter whole numbers greater than or equal to zero.',
      saved: 'Game library settings saved.',
      installed: 'Libraries saved. Open or restart Playnite to finish connecting the extension.',
      partial:
        'Some libraries could not sync yet. Check that the library manager is running; automatic sync will retry.',
      playniteBenefit:
        'Bring games from multiple stores into one Windows library, with Playnite artwork and launch settings.',
      playniteDetail:
        'Requires Playnite and the NOUS extension. Saving installs the extension only if you select Playnite.',
      steamBenefit: 'Sync your Steam games directly, with Steam artwork and launch support.',
      steamWindows: 'Use it instead of Playnite, or alongside it. No Playnite extension required.',
      steamDetail: 'Uses your local Steam library. No extra library manager required.',
      lutrisBenefit:
        'Add installed Linux games from Lutris, including games using Wine and other runners.',
      lutrisDetail:
        'Works alongside Steam. Steam games in Lutris are excluded from automatic sync by default.',
    },
    pt: {
      title: 'Conectar bibliotecas de jogos',
      configured: 'Configurações das bibliotecas',
      intro: 'Etapa {step} de 2 · {task}. Você pode alterar estas opções depois.',
      choose: 'Escolha as bibliotecas',
      configure: 'Configure a sincronização',
      loading: 'Carregando suas bibliotecas…',
      libraries: 'Bibliotecas para sincronizar automaticamente',
      recommended: 'Recomendado no Windows',
      settings: 'Opções de sincronização',
      none: 'A sincronização automática está desativada para todas as bibliotecas.',
      unavailable: 'Nenhuma biblioteca compatível está disponível neste PC.',
      all: 'Sincronizar todos os jogos instalados',
      recent: 'Jogos recentes',
      age: 'Idade máxima em dias (0 = sem limite)',
      remove: 'Remover jogos desinstalados da sincronização automática',
      tools: 'Incluir ferramentas do Steam',
      lutrisSteam: 'Incluir jogos do Steam cadastrados no Lutris',
      back: 'Voltar',
      done: 'Concluir',
      cancel: 'Cancelar',
      next: 'Próximo: opções das bibliotecas',
      save: 'Salvar configurações',
      saving: 'Salvando e sincronizando…',
      retry: 'Tentar novamente',
      loadError:
        'Não foi possível carregar as configurações das bibliotecas. Tente novamente antes de alterar opções.',
      installError:
        'Não foi possível instalar a extensão do Playnite. Instale o Playnite e tente novamente.',
      saveError:
        'Não foi possível salvar as configurações das bibliotecas. Suas alterações continuam disponíveis.',
      invalid: 'Informe números inteiros maiores ou iguais a zero.',
      saved: 'Configurações das bibliotecas salvas.',
      installed:
        'Bibliotecas salvas. Abra ou reinicie o Playnite para concluir a conexão da extensão.',
      partial:
        'Algumas bibliotecas ainda não puderam sincronizar. Confira se o gerenciador está aberto; a sincronização automática tentará novamente.',
      playniteBenefit:
        'Reúna jogos de várias lojas em uma biblioteca do Windows, com capas e opções de inicialização do Playnite.',
      playniteDetail:
        'Requer Playnite e extensão NOUS. Ao salvar, a extensão só será instalada se você selecionar Playnite.',
      steamBenefit:
        'Sincronize seus jogos diretamente do Steam, com capas e suporte à inicialização.',
      steamWindows: 'Use no lugar do Playnite ou junto dele. Não requer extensão do Playnite.',
      steamDetail: 'Usa sua biblioteca local do Steam. Não requer outro gerenciador.',
      lutrisBenefit:
        'Adicione jogos instalados no Linux pelo Lutris, incluindo jogos com Wine e outros executores.',
      lutrisDetail:
        'Funciona junto do Steam. Jogos do Steam no Lutris ficam fora da sincronização automática por padrão.',
    },
  },
});

const props = defineProps<{
  open: boolean;
  platform: string;
  metadata?: unknown;
  request: (
    method: 'GET' | 'POST' | 'PATCH',
    path: string,
    body?: Record<string, unknown>,
  ) => Promise<any>;
}>();
const emit = defineEmits<{
  (event: 'update:open', value: boolean): void;
  (event: 'saved'): void;
  (event: 'configured', value: boolean): void;
}>();
const dialog = ref<HTMLDialogElement>();
const titleId = `library-setup-${useId()}`;
let restoreFocusTo: HTMLElement | null = null;
let loadGeneration = 0;
const busy = ref(false);
const step = ref(1);
const configured = ref(false);
interface LibraryOptions {
  autosync_remove_uninstalled: boolean;
  sync_all_installed?: boolean;
  recent_games?: number;
  recent_max_age_days?: number;
  include_tools?: boolean;
  include_steam?: boolean;
}
const options = reactive<Record<string, LibraryOptions>>({});
function libraryOptions(id: string): LibraryOptions {
  return options[id]!;
}
const selectedLibraries = computed(() => libraries.value.filter((library) => selected[library.id]));
const loading = ref(false);
const settingsLoaded = ref(false);
const error = ref('');
const notice = ref('');
const config = ref<Record<string, unknown>>({});
const playniteInstalled = ref(false);
const selected = reactive<Record<string, boolean>>({
  playnite: false,
  steam: false,
  lutris: false,
});
const windows = computed(() => props.platform.toLowerCase().includes('windows'));
const linux = computed(() => props.platform.toLowerCase().includes('linux'));
const libraries = computed(() =>
  [
    ...(windows.value
      ? [
          {
            id: 'playnite',
            name: 'Playnite',
            benefit: t('playniteBenefit'),
            detail: t('playniteDetail'),
            recommended: true,
          },
        ]
      : []),
    {
      id: 'steam',
      name: 'Steam',
      benefit: t('steamBenefit'),
      detail: windows.value ? t('steamWindows') : t('steamDetail'),
      recommended: false,
    },
    ...(linux.value
      ? [
          {
            id: 'lutris',
            name: 'Lutris',
            benefit: t('lutrisBenefit'),
            detail: t('lutrisDetail'),
            recommended: false,
          },
        ]
      : []),
  ].filter((library) =>
    library.id === 'playnite' ? windows.value : providerSupported(props.metadata, library.id),
  ),
);
function flag(value: unknown, fallback: boolean): boolean {
  if (value === undefined) return fallback;
  return (
    value === true ||
    value === 1 ||
    ['true', 'on', '1', 'yes'].includes(String(value).toLowerCase())
  );
}
async function load() {
  const generation = ++loadGeneration;
  loading.value = true;
  settingsLoaded.value = false;
  error.value = '';
  notice.value = '';
  try {
    const [settings, playnite] = await Promise.all([
      props.request('GET', '/api/config'),
      windows.value ? props.request('GET', '/api/playnite/status') : Promise.resolve({}),
    ]);
    if (generation !== loadGeneration) return;
    if (settings.status === false) throw new Error('Unable to load settings');
    config.value = settings;
    playniteInstalled.value = playnite.installed === true || playnite.active === true;
    for (const library of libraries.value) {
      selected[library.id] =
        flag(settings[library.id + '_enabled'], true) &&
        flag(settings[library.id + '_auto_sync'], library.id !== 'steam') &&
        (library.id !== 'playnite' || playniteInstalled.value);
    }
    for (const { id } of libraries.value) {
      options[id] = {
        autosync_remove_uninstalled: flag(settings[id + '_autosync_remove_uninstalled'], true),
        ...(id !== 'lutris'
          ? {
              sync_all_installed: flag(settings[id + '_sync_all_installed'], false),
              recent_games: Number(settings[id + '_recent_games'] ?? 10),
              recent_max_age_days: Number(settings[id + '_recent_max_age_days'] ?? 30),
            }
          : { include_steam: flag(settings.lutris_include_steam, false) }),
        ...(id === 'steam' ? { include_tools: flag(settings.steam_include_tools, false) } : {}),
      };
    }
    configured.value = selectedLibraries.value.length > 0;
    emit('configured', configured.value);
    settingsLoaded.value = true;
  } catch {
    if (generation === loadGeneration) error.value = t('loadError');
  } finally {
    if (generation === loadGeneration) loading.value = false;
  }
}
async function save() {
  if (busy.value || !settingsLoaded.value || !libraries.value.length) return;
  if (
    selectedLibraries.value.some(({ id }) =>
      Object.entries(libraryOptions(id)).some(
        ([key, value]) =>
          ['recent_games', 'recent_max_age_days'].includes(key) &&
          (!Number.isSafeInteger(value) || Number(value) < 0),
      ),
    )
  ) {
    error.value = t('invalid');
    return;
  }
  busy.value = true;
  error.value = '';
  notice.value = '';
  try {
    let installed = false;
    if (windows.value && selected['playnite'] && !playniteInstalled.value) {
      const result = await props.request('POST', '/api/playnite/install', { restart: false });
      if (result.status === false || result.success === false)
        throw new Error(result.error || t('installError'));
      installed = true;
      playniteInstalled.value = true;
    }
    const patch: Record<string, unknown> = {};
    for (const library of libraries.value) {
      // Linux always exposes Steam discovery; its synchronization is opt-in.
      if (
        !(linux.value && library.id === 'steam') &&
        (library.id !== 'playnite' || providerSupported(props.metadata, 'playnite_toggle'))
      )
        patch[library.id + '_enabled'] = selected[library.id];
      patch[library.id + '_auto_sync'] = selected[library.id];
      if (selected[library.id]) {
        for (const [key, value] of Object.entries(libraryOptions(library.id))) {
          patch[library.id + '_' + key] = value;
        }
      }
    }
    const result = await props.request('PATCH', '/api/config', patch);
    if (result.status === false) throw new Error(result.error || t('saveError'));
    Object.assign(config.value, patch);
    notice.value = installed ? t('installed') : t('saved');
    configured.value = selectedLibraries.value.length > 0;
    emit('configured', configured.value);
    emit('saved');
    const results = await Promise.allSettled(
      selectedLibraries.value.map(async (library) => {
        const response = await props.request('POST', '/api/' + library.id + '/force_sync', {});
        if (response.status === false || response.success === false) throw new Error(library.name);
      }),
    );
    if (results.some((result) => result.status === 'rejected')) {
      notice.value += ' ' + t('partial');
    }
    emit('saved');
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : t('saveError');
  } finally {
    busy.value = false;
  }
}
function close() {
  if (!busy.value) emit('update:open', false);
}
watch(
  () => props.open,
  async (open) => {
    await nextTick();
    if (open) {
      step.value = 1;
      if (!dialog.value?.open) {
        restoreFocusTo =
          document.activeElement instanceof HTMLElement ? document.activeElement : null;
        dialog.value?.showModal();
        dialog.value?.focus();
      }
      void load();
    } else {
      ++loadGeneration;
      dialog.value?.close();
      if (restoreFocusTo?.isConnected) restoreFocusTo.focus();
      restoreFocusTo = null;
    }
  },
  { immediate: true },
);
watch(
  () => props.platform,
  (platform) => {
    if (platform && !props.open) void load();
  },
  { immediate: true },
);
onBeforeUnmount(() => {
  ++loadGeneration;
  dialog.value?.close();
  if (restoreFocusTo?.isConnected) restoreFocusTo.focus();
});
</script>

<template>
  <dialog
    ref="dialog"
    class="game-library-setup"
    :aria-labelledby="titleId"
    :aria-busy="loading || busy"
    tabindex="-1"
    @cancel.prevent="close"
  >
    <form @submit.prevent="step === 1 ? (step = 2) : save()">
      <header>
        <h2 :id="titleId">{{ t(configured ? 'configured' : 'title') }}</h2>
        <p>{{ t('intro', { step, task: t(step === 1 ? 'choose' : 'configure') }) }}</p>
      </header>
      <p v-if="loading" role="status">{{ t('loading') }}</p>
      <fieldset v-if="step === 1" :disabled="loading || busy || !settingsLoaded">
        <legend>{{ t('libraries') }}</legend>
        <p v-if="settingsLoaded && !libraries.length">{{ t('unavailable') }}</p>
        <label
          v-for="library in libraries"
          :key="library.id"
          class="game-library-setup__card"
          :class="{ 'game-library-setup__card--selected': selected[library.id] }"
        >
          <input v-model="selected[library.id]" type="checkbox" :aria-label="library.name" />
          <span>
            <strong
              >{{ library.name
              }}<small v-if="library.recommended">{{ t('recommended') }}</small></strong
            >
            <span>{{ library.benefit }}</span>
            <span class="game-library-setup__detail">{{ library.detail }}</span>
          </span>
        </label>
      </fieldset>
      <fieldset v-else :disabled="loading || busy || !settingsLoaded">
        <legend>{{ t('settings') }}</legend>
        <p v-if="!selectedLibraries.length">{{ t('none') }}</p>
        <section
          v-for="library in selectedLibraries"
          :key="library.id"
          class="game-library-setup__settings"
        >
          <h3>{{ library.name }}</h3>
          <template v-if="library.id !== 'lutris'">
            <label
              ><input v-model="libraryOptions(library.id).sync_all_installed" type="checkbox" />{{
                t('all')
              }}</label
            >
            <template v-if="!libraryOptions(library.id).sync_all_installed">
              <label
                >{{ t('recent')
                }}<input
                  v-model.number="libraryOptions(library.id).recent_games"
                  type="number"
                  min="0"
                  step="1"
                  required
              /></label>
              <label
                >{{ t('age')
                }}<input
                  v-model.number="libraryOptions(library.id).recent_max_age_days"
                  type="number"
                  min="0"
                  step="1"
                  required
              /></label>
            </template>
          </template>
          <label
            ><input
              v-model="libraryOptions(library.id).autosync_remove_uninstalled"
              type="checkbox"
            />{{ t('remove') }}</label
          >
          <label v-if="library.id === 'steam'"
            ><input v-model="libraryOptions('steam').include_tools" type="checkbox" />{{
              t('tools')
            }}</label
          >
          <label v-if="library.id === 'lutris'"
            ><input v-model="libraryOptions('lutris').include_steam" type="checkbox" />{{
              t('lutrisSteam')
            }}</label
          >
        </section>
      </fieldset>
      <div v-if="error" role="alert" class="game-library-setup__error">
        <p>{{ error }}</p>
        <button v-if="!settingsLoaded" type="button" :disabled="loading" @click="load">
          {{ t('retry') }}
        </button>
      </div>
      <p v-if="notice" role="status" class="game-library-setup__notice">{{ notice }}</p>
      <footer>
        <button v-if="step === 2" type="button" :disabled="busy" @click="step = 1">
          {{ t('back') }}
        </button>
        <button type="button" :disabled="busy" @click="close">
          {{ t(notice ? 'done' : 'cancel') }}
        </button>
        <button
          type="submit"
          class="game-library-setup__save"
          :disabled="loading || busy || !settingsLoaded || !libraries.length"
        >
          {{ t(busy ? 'saving' : step === 1 ? 'next' : 'save') }}
        </button>
      </footer>
    </form>
  </dialog>
</template>

<style scoped>
.game-library-setup {
  box-sizing: border-box;
  width: min(680px, calc(100vw - 32px));
  max-height: calc(100dvh - 32px);
  margin: auto;
  padding: clamp(16px, 3vw, 28px);
  overflow: auto;
  border: 1px solid var(--vs-color-border-subtle, #343d45);
  border-radius: var(--vs-radius-dialog, 24px);
  background: var(--vs-color-bg-surface, #1a2024);
  color: var(--vs-color-text-primary, #e1e3e6);
  box-shadow: var(--vs-shadow-overlay, 0 16px 48px #0006);
  font-family: inherit;
  overscroll-behavior: contain;
}
.game-library-setup::backdrop {
  background: var(--vs-overlay-backdrop, #0009);
}
.game-library-setup header {
  margin-bottom: 24px;
}
.game-library-setup h2 {
  margin: 0 0 8px;
  font-size: 24px;
  line-height: 1.25;
}
.game-library-setup p {
  line-height: 1.5;
  overflow-wrap: anywhere;
}
.game-library-setup header p,
.game-library-setup__detail {
  color: var(--vs-color-text-secondary, #b9c2ca);
}
.game-library-setup fieldset {
  border: 0;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 16px;
  min-width: 0;
}
.game-library-setup legend {
  margin-bottom: 12px;
  font-weight: 600;
}
.game-library-setup__card,
.game-library-setup__settings {
  padding: 16px;
  border: 1px solid var(--vs-color-border-subtle, #343d45);
  border-radius: var(--vs-radius-card, 20px);
}
.game-library-setup__card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  cursor: pointer;
}
.game-library-setup__card > span {
  min-width: 0;
}
.game-library-setup__card--selected {
  border-color: var(--vs-color-focus, #80d7df);
  background: color-mix(
    in srgb,
    var(--vs-color-focus, #80d7df) 10%,
    var(--vs-color-bg-surface, #1a2024)
  );
}
.game-library-setup input[type='checkbox'] {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  accent-color: var(--vs-color-focus, #80d7df);
}
.game-library-setup__card input {
  margin-top: 2px;
}
.game-library-setup__card span span {
  display: block;
  margin-top: 8px;
  line-height: 1.5;
}
.game-library-setup__card strong {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  font-size: 18px;
}
.game-library-setup__card small {
  font-size: 12px;
  font-weight: 500;
  color: var(--vs-color-text-secondary, #b9c2ca);
}
.game-library-setup__settings {
  display: grid;
  gap: 16px;
}
.game-library-setup__settings h3 {
  margin: 0;
}
.game-library-setup__settings label {
  display: flex;
  align-items: center;
  gap: 12px;
}
.game-library-setup input[type='number'] {
  width: 96px;
  min-height: 40px;
  flex-shrink: 0;
  margin-left: auto;
  padding: 8px;
  border: 1px solid var(--vs-color-border-subtle, #343d45);
  border-radius: var(--vs-radius-control, 20px);
  background: var(--vs-color-bg-subtle, #22292e);
  color: inherit;
  font: inherit;
}
.game-library-setup__error {
  color: var(--vs-color-status-danger, #ffb4ab);
}
.game-library-setup__notice {
  padding: 12px;
  border: 1px solid var(--vs-color-status-success, #80d7ac);
  border-radius: var(--vs-radius-control, 20px);
}
.game-library-setup footer {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
}
.game-library-setup button {
  min-height: 40px;
  padding: 8px 20px;
  border: 1px solid var(--vs-color-border-subtle, #343d45);
  border-radius: var(--vs-radius-control, 20px);
  background: var(--vs-color-bg-surface, #1a2024);
  color: inherit;
  cursor: pointer;
  font: inherit;
  font-weight: 600;
}
.game-library-setup button:hover:not(:disabled) {
  background: var(--vs-color-bg-subtle, #22292e);
}
.game-library-setup .game-library-setup__save {
  background: var(--vs-color-accent-default, #ff9eac);
  border-color: var(--vs-color-accent-default, #ff9eac);
  color: var(--vs-color-text-on-accent, #311018);
}
.game-library-setup .game-library-setup__save:hover:not(:disabled) {
  background: var(--vs-color-accent-hover, #ffb3bc);
}
.game-library-setup :focus-visible {
  outline: none;
  box-shadow: none;
  border-color: var(--vs-color-focus, #80d7df);
}
.game-library-setup input[type='checkbox']:focus-visible {
  outline: 2px solid var(--vs-color-focus, #80d7df);
  outline-offset: 2px;
}
.game-library-setup__card:has(:focus-visible) {
  border-color: var(--vs-color-focus, #80d7df);
}
.game-library-setup button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
@media (max-width: 480px) {
  .game-library-setup footer {
    flex-direction: column-reverse;
  }
  .game-library-setup footer button {
    width: 100%;
  }
  .game-library-setup__settings label:has(input[type='number']) {
    flex-direction: column;
    align-items: stretch;
  }
  .game-library-setup input[type='number'] {
    width: 100%;
    margin-left: 0;
  }
}
@media (forced-colors: active) {
  .game-library-setup :focus-visible {
    outline: 2px solid Highlight;
    outline-offset: 2px;
  }
  .game-library-setup__card--selected {
    border: 2px solid Highlight;
  }
}
</style>

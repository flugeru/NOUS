import { onBeforeUnmount, onMounted, ref, type ComputedRef } from 'vue';
import { onBeforeRouteLeave } from 'vue-router';

export const discardDialogOpen = ref(false);
let pending: Promise<boolean> | undefined;
let resolvePending: ((value: boolean) => void) | undefined;

export function confirmDiscard(): Promise<boolean> {
  if (pending) return pending;
  discardDialogOpen.value = true;
  pending = new Promise(resolve => { resolvePending = resolve; });
  return pending;
}

export function resolveDiscard(confirmed: boolean): void {
  discardDialogOpen.value = false;
  resolvePending?.(confirmed);
  resolvePending = undefined;
  pending = undefined;
}

export function useUnsavedChanges(dirty: ComputedRef<boolean>): void {
  function beforeUnload(event: BeforeUnloadEvent) {
    if (dirty.value) {
      event.preventDefault();
      event.returnValue = '';
    }
  }
  onBeforeRouteLeave(() => !dirty.value || confirmDiscard());
  onMounted(() => window.addEventListener('beforeunload', beforeUnload));
  onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload));
}

import { createI18n, type LocaleMessageValue } from 'vue-i18n';
import { browserLocale } from './utils/browserLocale';

type LocaleMessages = Record<string, LocaleMessageValue>;

async function loadJson(path: string): Promise<LocaleMessages> {
  const response = await fetch(path, {
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) throw new Error('locale-catalog-unavailable');
  return (await response.json()) as LocaleMessages;
}

function mergeMessages(base: LocaleMessages, overlay: LocaleMessages): LocaleMessages {
  const result: LocaleMessages = { ...base };
  for (const [key, value] of Object.entries(overlay)) {
    const existing = result[key];
    result[key] =
      value &&
      typeof value === 'object' &&
      !Array.isArray(value) &&
      existing &&
      typeof existing === 'object' &&
      !Array.isArray(existing)
        ? mergeMessages(existing as LocaleMessages, value as LocaleMessages)
        : value;
  }
  return result;
}

async function loadMessages(locale: string): Promise<LocaleMessages> {
  const englishBase = await loadJson('/v2/assets/locale/en.json');
  const englishInterface = await loadJson('/v2/assets/locale/ui/en.json');
  let messages = mergeMessages(englishBase, englishInterface);
  if (locale === 'en') return messages;

  const encoded = encodeURIComponent(locale);
  const base = await loadJson(`/v2/assets/locale/${encoded}.json`);
  messages = mergeMessages(messages, base);
  try {
    const interfaceMessages = await loadJson(`/v2/assets/locale/ui/${encoded}.json`);
    return mergeMessages(messages, interfaceMessages);
  } catch {
    return messages;
  }
}

function browserLocaleTag(locale: string): string {
  return locale.replace(/_/g, '-');
}

export async function createVibepolloI18n() {
  const configured = browserLocale(navigator.languages?.length ? navigator.languages : [navigator.language]);
  let activeLocale = 'en';
  const english = await loadMessages('en');
  const messages: Record<string, LocaleMessages> = { en: english };

  if (configured !== 'en') {
    try {
      const configuredMessages = await loadMessages(configured);
      activeLocale = browserLocaleTag(configured);
      messages[activeLocale] = configuredMessages;
    } catch {
      activeLocale = 'en';
    }
  }

  document.documentElement.lang = activeLocale;
  return createI18n({
    legacy: false,
    locale: activeLocale,
    fallbackLocale: 'en',
    fallbackWarn: false,
    missingWarn: false,
    messages,
  });
}

const supportedLocales = [
  'bg', 'cs', 'de', 'en', 'en_GB', 'en_US', 'es', 'fr', 'hu', 'it', 'ja', 'ko',
  'pl', 'pt', 'pt_BR', 'ru', 'sv', 'tr', 'uk', 'vi', 'zh', 'zh_TW',
];

export function browserLocale(languages: readonly string[]): string {
  for (const language of languages) {
    const tag = language.replace(/_/g, '-').toLowerCase();
    const exact = supportedLocales.find((locale) => locale.replace('_', '-').toLowerCase() === tag);
    if (exact) return exact;
    if (/^zh-(?:hant|tw|hk|mo)(?:-|$)/.test(tag)) return 'zh_TW';
    const base = tag.split('-')[0];
    if (supportedLocales.includes(base)) return base;
  }
  return 'en';
}

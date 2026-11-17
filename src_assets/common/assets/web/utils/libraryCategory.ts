export type LibraryCategory = 'desktop' | 'games' | 'launchers';

export function libraryCategory(app: Record<string, unknown>): LibraryCategory {
  const explicit = app['library-category'];
  if (explicit === 'desktop' || explicit === 'games' || explicit === 'launchers') return explicit;
  const effective = app['effective-library-category'];
  if (effective === 'desktop' || effective === 'games' || effective === 'launchers') return effective;
  if (app['remote-session'] || app['remote-monitor-profile'] === true) return 'desktop';
  if (app['playnite-fullscreen'] === true) return 'launchers';
  const command = Array.isArray(app.cmd) ? app.cmd.join(' ') : String(app.cmd ?? '');
  if (!command.trim() && !(Array.isArray(app.detached) && app.detached.length)
      && !app['playnite-id'] && !app['steam-id'] && !app['lutris-id']) return 'desktop';
  return 'games';
}

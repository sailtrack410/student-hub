/** First TypeScript module in the incremental admin migration. Legacy page components keep their registry contract. */
import { getPage } from './registry.js';
import { PAGE_FILES, ASSET_HASHES } from './asset-manifest.js';

const inflight = new Map<string, Promise<void>>();
// AI config embeds its test pane; load that dependency only on the AI route.
const dependencies: Record<string, string[]> = { 'aiconfig.js': ['aitest.js'] };

function loadScript(file: string): Promise<void> {
    const existing = inflight.get(file);
    if (existing) return existing;
    const path = '/admin/js/pages/' + file;
    const promise = Promise.all((dependencies[file] || []).map(loadScript)).then(() => new Promise<void>((resolve, reject) => {
        const script = document.createElement('script');
        script.src = path + '?v=' + ASSET_HASHES[path];
        script.async = true;
        script.onload = () => resolve();
        script.onerror = () => { script.remove(); inflight.delete(file); reject(new Error('页面资源加载失败，请检查网络后重试')); };
        document.head.appendChild(script);
    }));
    inflight.set(file, promise);
    promise.catch(() => inflight.delete(file));
    return promise;
}

export async function loadPage(name: string): Promise<object> {
    const cached = getPage(name);
    if (cached) return cached;
    const file = PAGE_FILES[name];
    if (!file) throw new Error('此页面未配置加载入口：' + name);
    await loadScript(file);
    const component = getPage(name);
    if (!component) throw new Error('页面资源尚未注册，请刷新后重试');
    return component;
}

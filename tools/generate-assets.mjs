#!/usr/bin/env node
// Student Hub UI 素材生成管线：
//   node tools/generate-assets.mjs            生成缺失的素材（已存在则跳过）
//   node tools/generate-assets.mjs --force    全部重新生成
//   node tools/generate-assets.mjs --only id1,id2
// 凭证来源：/Users/sailtrack/Desktop/.env（url/key）或环境变量 IMAGE_API_URL / IMAGE_API_KEY。
// 生图模型：gpt-image-2（OpenAI 兼容 /v1/images/generations，background=transparent）。

import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { ASSETS } from './assets/manifest.mjs';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const WEAPP = join(ROOT, 'student-hub-weapp');
const LOG_FILE = join(ROOT, 'tools/assets/generate.log');

const args = process.argv.slice(2);
const FORCE = args.includes('--force');
const onlyIdx = args.indexOf('--only');
const ONLY = onlyIdx >= 0 ? args[onlyIdx + 1].split(',').map((s) => s.trim()) : null;

function readCredentials() {
  if (process.env.IMAGE_API_URL && process.env.IMAGE_API_KEY) {
    return { url: process.env.IMAGE_API_URL, key: process.env.IMAGE_API_KEY };
  }
  const raw = readFileSync('/Users/sailtrack/Desktop/.env', 'utf8');
  const url = raw.match(/url\s*:\s*"([^"]+)"/)?.[1];
  const key = raw.match(/key\s*:\s*"([^"]+)"/)?.[1];
  if (!url || !key) throw new Error('无法从 /Users/sailtrack/Desktop/.env 解析 url/key');
  return { url, key };
}

const log = [];
function say(line) {
  const stamped = `[${new Date().toISOString().slice(11, 19)}] ${line}`;
  console.log(stamped);
  log.push(stamped);
}

function sips(pargs) {
  execFileSync('sips', pargs, { stdio: 'pipe' });
}

function postProcess(absPath, post) {
  if (!post || (!post.resize && !post.format)) return absPath;
  let current = absPath;
  if (post.resize) sips([current, '-Z', String(post.resize)]);
  // targetPath 已按 jpeg 落盘为 .jpg 时无需再转换，避免 in-place 转换自毁文件
  if (post.format === 'jpeg' && current.endsWith('.png')) {
    const target = current.replace(/\.png$/, '.jpg');
    sips([current, '-s', 'format', 'jpeg', '-s', 'formatOptions', String(post.quality || 75), '--out', target]);
    execFileSync('rm', [current]);
    current = target;
  }
  return current;
}

function targetPath(file) {
  // jpeg 后处理会把扩展名换成 .jpg
  const asset = ASSETS.find((a) => a.file === file);
  return asset?.post?.format === 'jpeg' ? file.replace(/\.png$/, '.jpg') : file;
}

async function generateOne(cred, asset) {
  const body = {
    model: 'gpt-image-2',
    prompt: asset.prompt,
    size: asset.size,
    n: 1,
  };
  if (asset.transparent) body.background = 'transparent';

  let lastError;
  for (let attempt = 1; attempt <= 3; attempt++) {
    try {
      const resp = await fetch(`${cred.url.replace(/\/$/, '')}/v1/images/generations`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${cred.key}`, 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
        signal: AbortSignal.timeout(280_000),
      });
      if (!resp.ok) throw new Error(`HTTP ${resp.status}: ${(await resp.text()).slice(0, 200)}`);
      const data = await resp.json();
      const item = data?.data?.[0];
      if (!item?.url) throw new Error(`响应缺少 url: ${JSON.stringify(data).slice(0, 200)}`);

      const imgResp = await fetch(item.url, { signal: AbortSignal.timeout(120_000) });
      if (!imgResp.ok) throw new Error(`下载失败 HTTP ${imgResp.status}`);
      const buf = Buffer.from(await imgResp.arrayBuffer());

      const abs = join(WEAPP, targetPath(asset.file));
      mkdirSync(dirname(abs), { recursive: true });
      writeFileSync(abs, buf);
      const finalAbs = postProcess(abs, asset.post);
      const kb = Math.round(existsSync(finalAbs) ? readFileSync(finalAbs).length / 1024 : 0);
      say(`✓ ${targetPath(asset.file)} (${kb}KB)`);
      return true;
    } catch (error) {
      lastError = error.message;
      say(`  ✗ ${asset.file} 第 ${attempt} 次失败: ${error.message}`);
      if (attempt < 3) await new Promise((r) => setTimeout(r, 5_000 * attempt));
    }
  }
  say(`✗✗ ${asset.file} 最终失败: ${lastError}`);
  return false;
}

async function main() {
  const cred = readCredentials();
  const list = ASSETS.filter((a) => !ONLY || ONLY.includes(a.file));
  say(`共 ${list.length} 个素材，并发 4 开始生成（force=${FORCE}）`);

  const queue = [...list];
  let ok = 0;
  const worker = async () => {
    while (queue.length) {
      const asset = queue.shift();
      const abs = join(WEAPP, targetPath(asset.file));
      if (!FORCE && existsSync(abs)) {
        say(`– 跳过（已存在）${targetPath(asset.file)}`);
        ok++;
        continue;
      }
      if (await generateOne(cred, asset)) ok++;
    }
  };
  await Promise.all(Array.from({ length: 4 }, worker));
  say(`完成：${ok}/${list.length} 成功`);
  writeFileSync(LOG_FILE, log.join('\n'));
  if (ok < list.length) process.exitCode = 1;
}

main();

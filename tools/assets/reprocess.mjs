#!/usr/bin/env node
// 对已下载的素材应用 manifest 的 post 规则（缩放 / 转 JPEG）。
// 用法：node tools/assets/reprocess.mjs [--force]
// 转换一律写入临时文件再替换，避免 sips in-place 输出异常。

import { execFileSync } from 'node:child_process';
import { existsSync, statSync, unlinkSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { ASSETS } from './manifest.mjs';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const WEAPP = join(ROOT, 'student-hub-weapp');
const FORCE = process.argv.includes('--force');

function sips(pargs) {
  execFileSync('sips', pargs, { stdio: 'pipe' });
}

function pngMaxEdge(file) {
  const out = execFileSync('sips', ['-g', 'pixelWidth', '-g', 'pixelHeight', file], { encoding: 'utf8' });
  const w = Number(out.match(/pixelWidth: (\d+)/)?.[1] || 0);
  const h = Number(out.match(/pixelHeight: (\d+)/)?.[1] || 0);
  return Math.max(w, h);
}

for (const asset of ASSETS) {
  const post = asset.post || {};
  if (!post.resize && post.format !== 'jpeg') continue;
  const target = post.format === 'jpeg' ? asset.file.replace(/\.png$/, '.jpg') : asset.file;
  const abs = join(WEAPP, target);
  // 历史遗留：目标是 .jpg 但磁盘上还是旧 .png 时，以 .png 为源转换
  const legacyPng = target.endsWith('.jpg') ? join(WEAPP, asset.file) : null;
  const source = !existsSync(abs) && legacyPng && existsSync(legacyPng) ? legacyPng : abs;
  if (!existsSync(source)) {
    console.log(`– 缺失，跳过 ${target}`);
    continue;
  }
  const maxEdge = source.endsWith('.jpg') ? 100000 : pngMaxEdge(source);
  if (!FORCE && post.resize && maxEdge <= post.resize) {
    console.log(`– 已达标（${maxEdge}px）${target}`);
    continue;
  }
  const temp = abs + '.tmp';
  execFileSync('cp', [source, temp]);
  if (post.resize) sips([temp, '-Z', String(post.resize)]);
  if (post.format === 'jpeg') {
    sips([temp, '-s', 'format', 'jpeg', '-s', 'formatOptions', String(post.quality || 70), '--out', temp + '.jpg']);
    unlinkSync(temp);
    execFileSync('mv', [temp + '.jpg', abs]);
    if (legacyPng && existsSync(legacyPng)) execFileSync('rm', [legacyPng]);
  } else {
    execFileSync('mv', [temp, abs]);
  }
  const kb = Math.round(statSync(abs).size / 1024);
  console.log(`✓ ${target} (${kb}KB)`);
}

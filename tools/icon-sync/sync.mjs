import { readFile, writeFile, mkdir, readdir, access, unlink } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../..');
const assets = path.join(root, 'app/src/main/assets/contact_logos');
const catalog = JSON.parse(await readFile(path.join(here, 'catalog.json'), 'utf8'));
const output = path.join(assets, 'thesvg');
const localOutput = path.join(assets, 'local');
await mkdir(output, { recursive: true });
await mkdir(localOutput, { recursive: true });
const exists = async p => access(p).then(() => true, () => false);
const normalize = name => name.toLowerCase().replace(/[^a-z0-9]/g, '');
const locals = await readdir(path.join(root, 'ICON')).catch(() => []);
const knownAssets = (await readdir(assets)).filter(n => n.endsWith('.svg'));
// Auto-sync source files whose names match established Android asset keys.
for (const filename of locals.filter(n => n.endsWith('.svg'))) {
  const key = knownAssets.find(n => normalize(n) === normalize(filename))?.slice(0, -4);
  if (key && !catalog[key]) catalog[key] = { local: filename, domains: [] };
}
const domains = {};
const report = [];
for (const [key, entry] of Object.entries(catalog).sort()) {
  if (!/^[a-z0-9._-]+$/.test(key)) throw new Error(`Invalid asset key: ${key}`);
  let svg, source, destination, license, upstreamUrl;
  if (entry.local && await exists(path.join(root, 'ICON', entry.local))) {
    svg = await readFile(path.join(root, 'ICON', entry.local), 'utf8');
    source = `ICON/${entry.local}`;
    destination = localOutput;
  } else if (entry.slug) {
    const { default: icon } = await import(`@thesvg/icons/${entry.slug}`);
    svg = icon.variants.mono || icon.svg;
    source = `@thesvg/icons/${entry.slug} (${icon.variants.mono ? 'mono' : 'default'})`;
    license = icon.license;
    upstreamUrl = icon.url;
    destination = output;
  } else continue;
  if (entry.removeBackgroundCircle) svg = svg.replace(/<circle\b[^>]*\/?\s*>/g, '');
  if (!svg.includes('<svg') || /<script\b|<foreignObject\b/i.test(svg)) throw new Error(`Invalid SVG: ${key}`);
  await writeFile(path.join(destination, `${key}.svg`), svg.trim() + '\n');
  // Only remove this tool's same-key generated counterpart, never hand-maintained overrides.
  const counterpart = path.join(destination === output ? localOutput : output, `${key}.svg`);
  if (await exists(counterpart)) await unlink(counterpart);
  for (const domain of entry.domains) domains[domain] = key;
  report.push({ key, source, license, upstreamUrl });
}
await writeFile(path.join(assets, 'domains.json'), JSON.stringify(domains, null, 2) + '\n');
await writeFile(path.join(output, 'sources.json'), JSON.stringify(report, null, 2) + '\n');
console.log(`Synced ${report.length} icons and ${Object.keys(domains).length} domains; existing overrides preserved.`);

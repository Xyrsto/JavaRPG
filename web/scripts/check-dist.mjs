// Checks the production build (spec 006, FR-010 and NFR-004): nothing in dist/ may point at another site, and the
// compressed size must stay inside the budget. Run after `npm run build`.
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { gzipSync } from 'node:zlib';

const dist = new URL('../dist', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
const BUDGET_BYTES = 150 * 1024;
// XML namespaces are identifiers, not requests.
const ALLOWED = [/^https?:\/\/www\.w3\.org\//];

function files(dir) {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    return statSync(path).isDirectory() ? files(path) : [path];
  });
}

const problems = [];
let total = 0;
for (const file of files(dist)) {
  const bytes = readFileSync(file);
  total += gzipSync(bytes).length;
  if (/\.(html|js|css|svg|json|map)$/.test(file)) {
    for (const match of bytes.toString('utf8').matchAll(/https?:\/\/[^\s"'`)<>]+/g)) {
      if (!ALLOWED.some((ok) => ok.test(match[0]))) {
        problems.push(`${file}: reference to another site: ${match[0]}`);
      }
    }
  }
}
if (total > BUDGET_BYTES) {
  problems.push(`compressed size ${total} bytes is over the budget of ${BUDGET_BYTES} bytes`);
}
if (problems.length > 0) {
  process.stderr.write(problems.join('\n') + '\n');
  process.exit(1);
}
process.stdout.write(`dist ok: ${total} bytes compressed, no references to other sites\n`);

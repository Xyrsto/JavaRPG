import { fileURLToPath } from 'node:url';
import { ESLint } from 'eslint';
import { describe, expect, it } from 'vitest';

const eslint = new ESLint({ cwd: fileURLToPath(new URL('..', import.meta.url)) });

async function messagesFor(code: string): Promise<string[]> {
  const [result] = await eslint.lintText(code, { filePath: 'src/sample.ts' });
  return (result?.messages ?? []).map((m) => `${m.ruleId}: ${m.message}`);
}

describe('the lint rules that keep text out of the markup', () => {
  it.each([
    ['innerHTML assignment', 'const el = document.body; el.innerHTML = "<b>x</b>";'],
    ['outerHTML assignment', 'const el = document.body; el.outerHTML = "<b>x</b>";'],
    ['insertAdjacentHTML', 'document.body.insertAdjacentHTML("beforeend", "<b>x</b>");'],
    ['document.write', 'document.write("<b>x</b>");'],
    ['eval', 'eval("1 + 1");'],
    ['new Function', 'const f = new Function("return 1"); f();'],
    ['console.log', 'console.log("player id");'],
  ])('rejects %s', async (_name, code) => {
    const messages = await messagesFor(code);
    expect(messages.length).toBeGreaterThan(0);
  });

  it('accepts plain text insertion', async () => {
    const messages = await messagesFor('const el = document.body; el.textContent = "hello"; export {};');
    expect(messages).toEqual([]);
  });
});

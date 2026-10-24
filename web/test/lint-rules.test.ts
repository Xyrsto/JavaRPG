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
    // Ways around the plain forms that must stay closed once screens show server and player text.
    ['innerHTML with a quoted key', 'const el = document.body; el["innerHTML"] = "<b>x</b>";'],
    ['innerHTML with a template key', 'const el = document.body; el[`outerHTML`] = "<b>x</b>";'],
    ['innerHTML appended', 'const el = document.body; el.innerHTML += "<b>x</b>";'],
    ['innerHTML in Object.assign', 'Object.assign(document.body, { innerHTML: "<b>x</b>" });'],
    ['quoted innerHTML key in an object', 'Object.assign(document.body, { "outerHTML": "<b>x</b>" });'],
    ['setHTMLUnsafe', 'document.body.setHTMLUnsafe("<b>x</b>");'],
    ['parseHTMLUnsafe', 'Document.parseHTMLUnsafe("<b>x</b>");'],
    ['createContextualFragment', 'document.createRange().createContextualFragment("<b>x</b>");'],
    ['DOMParser', 'new DOMParser().parseFromString("<b>x</b>", "text/html");'],
    ['an on-event attribute', 'document.body.setAttribute("onclick", "alert(1)");'],
    ['srcdoc', 'document.body.setAttribute("srcdoc", "<b>x</b>");'],
    ['Function without new', 'const f = Function("return 1"); f();'],
    ['window.eval', 'window.eval("1 + 1");'],
    ['setTimeout with a string', 'setTimeout("alert(1)", 10);'],
  ])('rejects %s', async (_name, code) => {
    const messages = await messagesFor(code);
    expect(messages.length).toBeGreaterThan(0);
  });

  it('accepts plain text insertion', async () => {
    const messages = await messagesFor('const el = document.body; el.textContent = "hello"; export {};');
    expect(messages).toEqual([]);
  });
});

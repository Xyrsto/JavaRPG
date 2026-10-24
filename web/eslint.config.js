import js from '@eslint/js';
import { defineConfig } from 'eslint/config';
import tseslint from 'typescript-eslint';

// Everything the page shows is text. Nothing may turn text into markup or code (spec 002, NFR-004).
// The selectors cover the plain forms and the usual ways around them (quoted keys, object keys, the "unsafe"
// parsers, a parser or a range, event attributes), because later screens render server and player text.
const HTML_PROPERTY = '/^(innerHTML|outerHTML)$/';
const restricted = [
  { selector: `AssignmentExpression[left.property.name=${HTML_PROPERTY}]`, message: 'Insert text with textContent, never as markup.' },
  { selector: `AssignmentExpression[left.computed=true][left.property.value=${HTML_PROPERTY}]`, message: 'Insert text with textContent, never as markup.' },
  { selector: `AssignmentExpression[left.computed=true][left.property.quasis.0.value.raw=${HTML_PROPERTY}]`, message: 'Insert text with textContent, never as markup.' },
  { selector: `Property[key.name=${HTML_PROPERTY}]`, message: 'Do not set innerHTML or outerHTML, not even through an object.' },
  { selector: `Property[key.value=${HTML_PROPERTY}]`, message: 'Do not set innerHTML or outerHTML, not even through an object.' },
  { selector: "CallExpression[callee.property.name='insertAdjacentHTML']", message: 'Insert text with textContent, never as markup.' },
  { selector: "CallExpression[callee.property.name=/^(setHTMLUnsafe|parseHTMLUnsafe|createContextualFragment)$/]", message: 'These parse text as markup.' },
  { selector: "NewExpression[callee.name='DOMParser']", message: 'DOMParser parses text as markup.' },
  { selector: "CallExpression[callee.object.name='document'][callee.property.name=/^(write|writeln)$/]", message: 'document.write inserts markup.' },
  { selector: "CallExpression[callee.property.name='setAttribute'][arguments.0.value=/^(on.*|srcdoc)$/i]", message: 'Event attributes and srcdoc run or parse text as code or markup.' },
  { selector: "NewExpression[callee.name='Function']", message: 'Do not build code from text.' },
  { selector: "CallExpression[callee.name='Function']", message: 'Do not build code from text.' },
];

export default defineConfig(
  { ignores: ['dist/**', 'node_modules/**', 'src/api/schema.d.ts', 'scripts/**'] },
  js.configs.recommended,
  tseslint.configs.strict,
  {
    files: ['**/*.ts', '**/*.js'],
    languageOptions: {
      // The rules about code built from text need to know these browser globals.
      globals: { window: 'readonly', document: 'readonly', setTimeout: 'readonly', setInterval: 'readonly' },
    },
    rules: {
      'no-restricted-syntax': ['error', ...restricted],
      'no-eval': 'error',
      'no-implied-eval': 'error',
      'no-new-func': 'error',
      // The player id is a credential: the client writes no logs.
      'no-console': 'error',
    },
  },
);

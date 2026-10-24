import js from '@eslint/js';
import { defineConfig } from 'eslint/config';
import tseslint from 'typescript-eslint';

// Everything the page shows is text. Nothing may turn text into markup or code (spec 002, NFR-004).
const restricted = [
  {
    selector: 'AssignmentExpression[left.property.name=/^(innerHTML|outerHTML)$/]',
    message: 'Insert text with textContent, never as markup.',
  },
  {
    selector: "CallExpression[callee.property.name='insertAdjacentHTML']",
    message: 'Insert text with textContent, never as markup.',
  },
  {
    selector: "CallExpression[callee.object.name='document'][callee.property.name=/^(write|writeln)$/]",
    message: 'document.write inserts markup.',
  },
  {
    selector: "NewExpression[callee.name='Function']",
    message: 'Do not build code from text.',
  },
];

export default defineConfig(
  { ignores: ['dist/**', 'node_modules/**', 'src/api/schema.d.ts', 'scripts/**'] },
  js.configs.recommended,
  tseslint.configs.strict,
  {
    files: ['**/*.ts', '**/*.js'],
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

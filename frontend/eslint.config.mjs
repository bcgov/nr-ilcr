import { defineConfig } from 'eslint/config';
import tseslint from 'typescript-eslint';
import eslintReact from '@eslint-react/eslint-plugin';
import prettierPlugin from 'eslint-plugin-prettier';
import prettierConfig from 'eslint-config-prettier';

const baseIgnores = [
  '**/vite.config.*',
  '**/vitest.config.*',
  '**/playwright.config.*',
  '**/tsconfig*.json',
  '**/dist/**',
  '**/node_modules/**',
  '**/coverage/**',
];

export default defineConfig([
  {
    ignores: [
      ...baseIgnores,
      '**/public/**',
      'src/routeTree.gen.ts',
    ],
  },
  ...tseslint.configs.recommended,
  eslintReact.configs['recommended-typescript'],
  {
    files: ['**/*.ts', '**/*.tsx'],
    plugins: {
      prettier: prettierPlugin,
    },
    rules: {
      'prettier/prettier': 'error',

      'no-console': 'off',
      'no-debugger': 'warn',
      'no-unused-vars': 'off',
      'no-empty': ['error', { allowEmptyCatch: true }],

      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
      '@typescript-eslint/explicit-module-boundary-types': 'off',
      '@typescript-eslint/no-explicit-any': 'off',
      '@typescript-eslint/no-non-null-assertion': 'off',
      '@typescript-eslint/no-empty-interface': 'off',
      '@typescript-eslint/ban-types': 'off',
      '@typescript-eslint/explicit-function-return-type': 'off',

      'no-use-before-define': 'off',
      '@typescript-eslint/no-use-before-define': ['error', { functions: false }],
      '@typescript-eslint/no-var-requires': 'off',
      '@typescript-eslint/consistent-type-imports': ['error', { prefer: 'type-imports' }],

      'react/prop-types': 'off',

      // #321: a Carbon <TableContainer title> wires its heading to the <Table> through
      // aria-labelledby, which wins the accessible-name computation — so an aria-label on that
      // table is never heard, and its text quietly drifts from the name users get. Ten tables
      // carried one. This is an AST selector, so it reads real TSX: a `>` inside an earlier prop
      // (`isSortable={n > 0}`, `onX={() => ...}`), spaces around `=`, template-literal values or
      // a Table nested deeper in the container make no difference to it.
      //
      // Shape: a JSXElement whose opening tag is `TableContainer` carrying a `title` attribute,
      // with a `Table` opening tag anywhere below it that carries `aria-label`. The child
      // combinators are nested `:has(> …)` on purpose — esquery does not honour a chained
      // `:has(> A > B)`, and a plain descendant `:has(A > B)` would also match the component's
      // outer <div> and flag the untitled sibling tables that legitimately keep their label.
      // `components/__tests__/table-accessible-name.test.ts` pins both of those.
      //
      // Known limit: a Table inside an UNTITLED TableContainer that itself sits inside a titled
      // one is flagged although its label is live (esquery cannot say "nearest"). Nothing in the
      // app nests containers; if you must, or if a title is genuinely conditional and the label is
      // its fallback, disable this line with the reason. Otherwise delete the aria-label.
      'no-restricted-syntax': [
        'error',
        {
          selector:
            'JSXElement:has(> JSXOpeningElement[name.name="TableContainer"]:has(> JSXAttribute[name.name="title"])) JSXOpeningElement[name.name="Table"] > JSXAttribute[name.name="aria-label"]',
          message:
            '#321: this aria-label is dead — the enclosing TableContainer title names the table via aria-labelledby. Delete it (or disable with a reason if the title is conditional).',
        },
      ],
    },
  },
  prettierConfig,
]);

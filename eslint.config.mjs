// @ts-check

import globals from 'globals';
import prettier from 'eslint-plugin-prettier/recommended';
import tseslint from 'typescript-eslint';
import eslint from '@eslint/js';
// For a detailed explanation, visit: https://github.com/angular-eslint/angular-eslint/blob/main/docs/CONFIGURING_FLAT_CONFIG.md
import angular from 'angular-eslint';
// jhipster-needle-eslint-add-import - JHipster will add additional import here

export default tseslint.config(
  {
    languageOptions: {
      globals: {
        ...globals.node,
      },
    },
  },
  { ignores: ['src/main/docker/'] },
  { ignores: ['build/resources/main/static/', 'build/'] },
  eslint.configs.recommended,
  {
    files: ['**/*.{js,cjs,mjs}'],
    rules: {
      'no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
    },
  },
  {
    files: ['src/main/webapp/**/*.ts'],
    extends: [...tseslint.configs.strictTypeChecked, ...tseslint.configs.stylistic, ...angular.configs.tsRecommended],
    languageOptions: {
      globals: {
        ...globals.browser,
      },
      parserOptions: {
        project: ['./tsconfig.app.json', './tsconfig.spec.json'],
      },
    },
    processor: angular.processInlineTemplates,
    rules: {
      '@angular-eslint/component-selector': [
        'error',
        {
          type: 'element',
          prefix: 'jhi',
          style: 'kebab-case',
        },
      ],
      '@angular-eslint/directive-selector': [
        'error',
        {
          type: 'attribute',
          prefix: 'jhi',
          style: 'camelCase',
        },
      ],
      '@angular-eslint/relative-url-prefix': 'error',
      '@typescript-eslint/consistent-type-definitions': 'off',
      '@typescript-eslint/explicit-function-return-type': ['error', { allowExpressions: true }],
      '@typescript-eslint/explicit-module-boundary-types': 'off',
      '@typescript-eslint/member-ordering': [
        'error',
        {
          default: [
            'public-static-field',
            'protected-static-field',
            'private-static-field',
            'public-instance-field',
            'protected-instance-field',
            'private-instance-field',
            'constructor',
            'public-static-method',
            'protected-static-method',
            'private-static-method',
            'public-instance-method',
            'protected-instance-method',
            'private-instance-method',
          ],
        },
      ],
      '@typescript-eslint/no-confusing-void-expression': 'off',
      '@typescript-eslint/no-empty-object-type': 'off',
      '@typescript-eslint/no-explicit-any': 'off',
      '@typescript-eslint/no-extraneous-class': 'off',
      '@typescript-eslint/no-floating-promises': 'off',
      '@typescript-eslint/no-non-null-assertion': 'off',
      '@typescript-eslint/no-shadow': ['error'],
      '@typescript-eslint/no-unnecessary-condition': 'error',
      '@typescript-eslint/no-unsafe-argument': 'off',
      '@typescript-eslint/no-unsafe-assignment': 'off',
      '@typescript-eslint/no-unsafe-call': 'off',
      '@typescript-eslint/no-unsafe-member-access': 'off',
      '@typescript-eslint/no-unused-vars': 'error',
      '@typescript-eslint/ban-ts-comment': 'error',
      '@typescript-eslint/no-deprecated': 'error',
      '@typescript-eslint/prefer-nullish-coalescing': 'error',
      '@typescript-eslint/prefer-optional-chain': 'error',
      '@typescript-eslint/restrict-template-expressions': ['error', { allowNumber: true }],
      '@typescript-eslint/unbound-method': 'off',
      'arrow-body-style': 'error',
      curly: 'error',
      eqeqeq: ['error', 'always', { null: 'ignore' }],
      'guard-for-in': 'error',
      'no-bitwise': 'error',
      'no-caller': 'error',
      'no-console': ['error', { allow: ['warn', 'error'] }],
      'no-eval': 'error',
      'no-labels': 'error',
      'no-new': 'error',
      'no-new-wrappers': 'error',
      'object-shorthand': ['error', 'always', { avoidExplicitReturnArrows: true }],
      radix: 'error',
      'spaced-comment': ['warn', 'always'],
    },
  },
  {
    files: ['src/main/webapp/**/*.spec.ts'],
    rules: {
      '@typescript-eslint/no-empty-function': 'off',
      '@typescript-eslint/ban-ts-comment': 'error',
      '@typescript-eslint/no-deprecated': 'error',
    },
  },
  {
    files: ['**/*.html'],
    extends: [...angular.configs.templateRecommended, ...angular.configs.templateAccessibility],
    rules: {},
  },
  {
    // Html templates requires some work
    ignores: ['**/*.html'],
    extends: [prettier],
  },
  // jhipster-needle-eslint-add-config - JHipster will add additional config here
  prettier,
);

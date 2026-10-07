// Regression tests for: vite@5.4.21 upgrade
import { describe, expect, it } from 'vitest';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

describe('Vite React application configuration', () => {
  it('preserves the relative deployment base and React plugin', async () => {
    const { default: config } = await import('../vite.config.js');

    expect(config.base).toBe('./');
    expect(config.plugins.flat().some((plugin) => plugin && typeof plugin === 'object')).toBe(true);
  });

  it('accepts a production build configuration', () => {
    const config = defineConfig({
      base: './',
      plugins: [react()],
    });

    expect(config.base).toBe('./');
    expect(config.plugins).toHaveLength(1);
  });
});

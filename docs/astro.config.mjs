// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

// https://astro.build/config
export default defineConfig({
	site: 'https://bridje.github.io',
	integrations: [
		starlight({
			title: 'Bridje',
			social: [
				{ icon: 'github', label: 'GitHub', href: 'https://github.com/bridje/bridje' }
			],
			sidebar: [],
		}),
	],
});

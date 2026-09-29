// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import bridjeGrammar from './bridje.tmLanguage.json' with { type: 'json' };

// https://astro.build/config
export default defineConfig({
	site: 'https://bridje.github.io',
	integrations: [
		starlight({
			title: 'Bridje',
			logo: { src: './src/assets/logo.svg' },
			head: [
				{ tag: 'link', attrs: { rel: 'apple-touch-icon', href: '/apple-touch-icon.png' } },
			],
			expressiveCode: {
				shiki: { langs: [bridjeGrammar] },
			},
			social: [
				{ icon: 'github', label: 'GitHub', href: 'https://github.com/bridje/bridje' }
			],
			sidebar: [],
		}),
	],
});

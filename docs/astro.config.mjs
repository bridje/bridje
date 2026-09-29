// @ts-check
import { defineConfig } from 'astro/config';
import { unified } from '@astrojs/markdown-remark';
import starlight from '@astrojs/starlight';
import { remarkDefinitionList, defListHastHandlers } from 'remark-definition-list';
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
			sidebar: [
				{
					label: 'Start here',
					items: [
						'index',
						'getting-started',
						'getting-started/editors',
					],
				},
				{
					label: 'A tour of Bridje',
					items: [
						'tour/first-steps',
						'tour/functions',
						'tour/records',
						'tour/tags-and-enums',
						'tour/effects',
						'tour/namespaces-and-java',
					],
				},
				{
					label: 'Coming from…',
					items: [
						'coming-from/clojure',
						'coming-from/java-kotlin',
					],
				},
				{
					label: 'About Bridje',
					items: [
						'about/rationale',
						'about/types',
						'about/effects',
						'about/idioms',
					],
				},
				{
					label: 'Reference',
					items: [
						'reference/syntax',
						'reference/special-forms',
						'reference/core',
						'reference/records-tags-enums',
						'reference/types',
						'reference/effects',
						'reference/namespaces',
						'reference/interop',
						'reference/macros',
						'reference/errors',
						'reference/stdlib',
					],
				},
			],
		}),
	],
	markdown: {
		processor: unified({
			remarkPlugins: [remarkDefinitionList],
			remarkRehype: {
				handlers: { ...defListHastHandlers },
			},
		}),
	},
});

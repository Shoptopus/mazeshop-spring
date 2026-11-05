import { readFileSync, writeFileSync } from 'node:fs';
const path = process.argv[2];
if (!path) throw new Error('Pass the generated storefront index.html path.');
let html = readFileSync(path, 'utf8');
if (!html.includes('</head>')) throw new Error('The generated storefront has no head element.');
if (!html.includes('/brand/mazeshop.css')) html = html.replace('</head>', '<link rel="stylesheet" href="/brand/mazeshop.css">\n</head>');
html = html.replace(/<title>[^<]*<\/title>/, '<title>Mazeshop | Personalised gifts &amp; laser-crafted design</title>');
writeFileSync(path, html);

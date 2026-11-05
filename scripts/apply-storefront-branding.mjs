import { readFileSync, writeFileSync, copyFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
const path = process.argv[2];
if (!path) throw new Error('Pass the generated storefront index.html path.');
let html = readFileSync(path, 'utf8');
if (!html.includes('</head>')) throw new Error('The generated storefront has no head element.');
if (!html.includes('/brand/mazeshop.css')) html = html.replace('</head>', '<link rel="stylesheet" href="/brand/mazeshop.css">\n</head>');
html = html.replace(/<title>[^<]*<\/title>/, '<title>Mazeshop | Personalised gifts &amp; laser-crafted design</title>');
writeFileSync(path, html);

for (const filename of ['favicon.svg','favicon.ico','favicon-96x96.png','apple-touch-icon.png','web-app-manifest-192x192.png','web-app-manifest-512x512.png','site.webmanifest']) {
  copyFileSync(join(dirname(path), 'brand', 'icons', filename), join(dirname(path), filename));
}

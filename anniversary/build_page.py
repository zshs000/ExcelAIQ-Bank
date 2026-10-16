"""Build an offline, dependency-free HTML file. Does not change the Git snapshot."""
import json
import pathlib

root = pathlib.Path(__file__).resolve().parent
page = (root / 'page.html').read_text(encoding='utf-8')
sources = {'/*__STYLE__*/': 'style.css', '/*__CAKE_STYLE__*/': 'cake.css', '/*__CONTENT__*/': 'content.js', '/*__APP__*/': 'app.js'}
for token, name in sources.items():
    page = page.replace(token, (root / name).read_text(encoding='utf-8'))
history = json.loads((root / 'history.json').read_text(encoding='utf-8'))
page = page.replace('__COMMIT_COUNT__', str(history['count']))
page = page.replace('/*__DATA__*/', json.dumps(history, ensure_ascii=False).replace('<', '\\u003c'))
assert all(token not in page for token in sources)
target = root / 'dist' / 'index.html'
target.parent.mkdir(exist_ok=True)
target.write_text(page, encoding='utf-8')
print(f'Built {target} ({target.stat().st_size:,} bytes)')

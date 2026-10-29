import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).parent))
import build_catalog as catalog


class CatalogBuilderTest(unittest.TestCase):
    def test_description_extraction(self):
        html = '<p><b>Special Containment Procedures:</b> Keep away.</p><p><b>Description:</b> An entity that appears human but produces unusual changes when photographed.</p><p>Addendum: Later events.</p>'
        summary = catalog.summarize(html)
        self.assertIn('An entity', summary)
        self.assertNotIn('Keep away', summary)
        self.assertNotIn('Later events', summary)

    def test_unusual_article_is_not_fabricated(self):
        self.assertEqual(catalog.summarize('<p>Experimental interactive fiction with no formal description.</p>'), '')

    def test_series_aliases(self):
        parser = catalog.SeriesTitles()
        parser.feed('<ul><li><a href="/scp-049">SCP-049</a> - Plague Doctor</li><li><a href="/scp-096">SCP-096</a> - The "Shy Guy"</li></ul>')
        self.assertEqual(parser.names['scp-049'], 'Plague Doctor')
        self.assertEqual(parser.names['scp-096'], 'The "Shy Guy"')

    def test_generate_catalog_and_join_content(self):
        fake_index = {
            f'scp-{i:04d}': {
                'link': f'scp-{i:04d}',
                'scp': f'SCP-{i:04d}',
                'title': f'SCP-{i:04d}',
                'content_file': 'content_1.json',
                'images': [],
            }
            for i in range(1200)
        }
        fake_content = {
            'scp-0049': {'link': 'scp-0049', 'raw_content': '<div>Description: A surprising anomaly with many unexplained effects that merit further analysis.</div>'}
        }
        def fetch(url, max_bytes=175_000_000):
            if url.endswith('index.json'):
                return fake_index
            if url.endswith('content_1.json'):
                return fake_content
            raise AssertionError(url)
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'catalog.json'
            with patch.object(catalog, 'download_json', fetch), patch.object(catalog, 'series_titles', lambda: {'scp-0049': 'Test Codename'}), patch.object(sys, 'argv', ['build_catalog.py', '--out', str(path)]):
                catalog.main()
            result = json.loads(path.read_text(encoding='utf-8'))
            self.assertEqual(len(result['entries']), 1200)
            self.assertEqual(result['entries']['scp-0049']['display_name'], 'Test Codename')
            self.assertIn('A surprising anomaly', result['entries']['scp-0049']['summary'])

if __name__ == '__main__':
    unittest.main()

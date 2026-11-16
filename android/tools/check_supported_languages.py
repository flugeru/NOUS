#!/usr/bin/env python3
"""Check app-owned string coverage and format placeholders for supported locales."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

res = Path(__file__).resolve().parents[1] / 'app/src/main/res'

def strings(folder):
    result = {}
    for file in (res / folder).glob('*.xml'):
        for node in ET.parse(file).getroot():
            if node.tag == 'string':
                name = node.attrib['name']
                assert name not in result, f'duplicate {folder}/{name}'
                result[name] = node
    return result

english = strings('values')
portuguese = strings('values-pt-rBR')
missing = sorted(name for name, node in english.items()
                 if node.get('translatable') != 'false' and name not in portuguese)
assert not missing, f'Missing pt-BR strings: {missing}'
# Check added translations; older translations need a separate semantic review.
pattern = re.compile(r'%(?:(\d+)\$)[-#+ 0,(<]*\d*(?:\.\d+)?([sdf])|%([sdf%])(?=$|[^a-zA-Z])')
for filename in ('nous_completed_strings.xml', 'nous_missing_strings.xml'):
    for node in ET.parse(res / 'values-pt-rBR' / filename).getroot():
        name = node.attrib['name']
        source = ''.join(english[name].itertext())
        translation = ''.join(node.itertext())
        assert [m.group(2) or m.group(3) for m in pattern.finditer(source)] == [m.group(2) or m.group(3) for m in pattern.finditer(translation)], name
print(f'OK: {len(english)} English strings; all translatable strings have pt-BR resources')

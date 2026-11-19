"""Generate NOUS icons from RGBA PNG, optionally original SVG and ICO.

Usage: python tools/update_branding.py icon.png [icon.svg icon.ico]
Requires Pillow. Preserve supplied alpha/colors; Android composites onto black.
"""
import base64
from pathlib import Path
import sys
import shutil

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SIZES = [(n, n) for n in (16, 24, 32, 48, 64, 128, 256)]


def copy_source(source, target):
    if source.resolve() != target.resolve():
        shutil.copyfile(source, target)


def save_icon(image, path):
    image.save(path, format='ICO', sizes=SIZES)
    with Image.open(path) as icon:
        assert icon.ico.sizes() == set(SIZES), (path, icon.ico.sizes())


def save_svg(image_path, svg_path):
    data = base64.b64encode(image_path.read_bytes()).decode('ascii')
    with Image.open(image_path) as image:
        w, h = image.size
    svg_path.write_text(f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}"><image width="{w}" height="{h}" href="data:image/png;base64,{data}"/></svg>\n', encoding='utf-8')


def main(source_path, svg_path=None, ico_path=None):
    with Image.open(source_path) as source:
        assert min(source.size) >= 256, 'Source >=256px required'
        source = source.convert('RGBA')
        side = max(source.size)
        image = Image.new('RGBA', (side, side))
        image.alpha_composite(source, ((side - source.width) // 2, (side - source.height) // 2))
    black = Image.new('RGB', image.size, 'black')
    black.paste(image, mask=image.getchannel('A'))
    assert image.getpixel((0, 0))[3] == 0
    brand = ROOT / 'branding/NOUS'
    black.save(brand / 'new-project-icon-black.png')
    save_icon(black, brand / 'new-project-icon-black.ico')
    image.save(brand / 'new-project-icon-transparent.png')
    save_icon(image, brand / 'new-project-icon-transparent.ico')
    image.save(ROOT / 'nous.png')
    save_icon(image, ROOT / 'nous.ico')
    image.save(ROOT / 'nous.icns', format='ICNS')
    save_svg(ROOT / 'nous.png', ROOT / 'nous.svg')
    image.save(ROOT / 'src_assets/macos/build/nous.icns', format='ICNS')
    for web in ('web', 'web-legacy'):
        folder = ROOT / 'src_assets/common/assets' / web / 'public/images'
        save_icon(image, folder / 'nous.ico')
        save_svg(ROOT / 'nous.png', folder / 'logo-nous.svg')
        for size in (16, 45):
            image.resize((size, size), Image.Resampling.LANCZOS).save(folder / f'logo-nous-{size}.png')
        for status, color in (('locked', '#ffc107'), ('pausing', '#ff9800'), ('playing', '#00dcb4')):
            variant = image.copy()
            d = ImageDraw.Draw(variant)
            w = image.width
            box = (int(w * .70), int(w * .70), int(w * .96), int(w * .96))
            d.ellipse(box, fill=color, outline='#ffffff', width=max(1, w // 100))
            variant.save(folder / f'nous-{status}.png')
            save_icon(variant, folder / f'nous-{status}.ico')
            save_svg(folder / f'nous-{status}.png', folder / f'nous-{status}.svg')
            for size in (16, 45):
                variant.resize((size, size), Image.Resampling.LANCZOS).save(folder / f'nous-{status}-{size}.png')
    res = ROOT / 'android/app/src/main/res'
    image.resize((512, 512), Image.Resampling.LANCZOS).save(res / 'drawable-nodpi/nous_brand.png')
    for density, size in (('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)):
        folder = res / f'mipmap-{density}'
        black.resize((size, size), Image.Resampling.LANCZOS).save(folder / 'ic_launcher.png')
        canvas_size = round(size * 108 / 48)
        foreground = Image.new('RGB', (canvas_size, canvas_size), 'black')
        emblem_size = round(canvas_size * 72 / 108)
        foreground.paste(black.resize((emblem_size, emblem_size), Image.Resampling.LANCZOS), ((canvas_size - emblem_size) // 2,) * 2)
        foreground.save(folder / 'ic_launcher_foreground.png')
        with Image.open(folder / 'ic_launcher.png') as icon:
            assert icon.mode == 'RGB' and icon.getpixel((0, 0)) == (0, 0, 0)
    black.resize((512, 512), Image.Resampling.LANCZOS).save(res / 'drawable/app_icon.png')
    black.resize((512, 512), Image.Resampling.LANCZOS).save(ROOT / 'android/app/src/main/ic_launcher-web.png')
    black.resize((512, 512), Image.Resampling.LANCZOS).save(ROOT / 'android/fastlane/metadata/android/en-US/images/icon.png')
    banner = Image.new('RGB', (732, 412), 'black')
    banner.paste(black.resize((412, 412), Image.Resampling.LANCZOS), (160, 0))
    banner.save(res / 'drawable-xhdpi/ouya_icon.png')
    if svg_path:
        copy_source(svg_path, brand / 'nous-tricolor-alinhado.svg')
        for target in (ROOT / 'nous.svg', *(ROOT / 'src_assets/common/assets' / web / 'public/images/logo-nous.svg' for web in ('web', 'web-legacy'))):
            copy_source(svg_path, target)
    if ico_path:
        with Image.open(ico_path) as supplied:
            assert supplied.format == 'ICO' and max(w for w, h in supplied.ico.sizes()) >= 256
        for target in (ROOT / 'nous.ico', brand / 'new-project-icon-transparent.ico', *(ROOT / 'src_assets/common/assets' / web / 'public/images/nous.ico' for web in ('web', 'web-legacy'))):
            copy_source(ico_path, target)
    print('NOUS: source alpha/colors preserved, Android opaque, icon variants verified.')


if __name__ == '__main__':
    main(*(Path(arg) for arg in sys.argv[1:]))

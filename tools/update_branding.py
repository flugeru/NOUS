"""Generate NOUS icons. Usage: python tools/update_branding.py path/to/black.png

Requires Pillow. Desktop uses alpha; Android always composites onto black.
"""
import base64
from pathlib import Path
import sys

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SIZES = [(n, n) for n in (16, 24, 32, 48, 64, 128, 256)]


def transparent(source):
    image = source.convert('RGBA')
    pixels = []
    for r, g, b, _ in image.get_flattened_data():
        # Remove black matte, including near-black antialiasing, without dark fringes.
        alpha = round(255 * max(0, min(1, (max(r, g, b) - 20) / 44)))
        pixels.append((*(min(255, round(c * 255 / alpha)) if alpha else 0 for c in (r, g, b)), alpha))
    image.putdata(pixels)
    return image


def save_icon(image, path):
    image.save(path, format='ICO', sizes=SIZES)
    with Image.open(path) as icon:
        assert icon.ico.sizes() == set(SIZES), (path, icon.ico.sizes())


def save_svg(image_path, svg_path):
    data = base64.b64encode(image_path.read_bytes()).decode('ascii')
    svg_path.write_text(f'<svg xmlns="http://www.w3.org/2000/svg" width="1254" height="1254" viewBox="0 0 1254 1254"><image width="1254" height="1254" href="data:image/png;base64,{data}"/></svg>\n', encoding='utf-8')


def main(source_path):
    with Image.open(source_path) as source:
        assert source.width == source.height and source.width >= 256, 'Square source >=256px required'
        black = source.convert('RGB')
    image = transparent(black)
    assert image.getpixel((0, 0))[3] == 0
    assert image.getpixel((image.width // 2, image.height // 2))[3] == 255
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
    black.resize((512, 512), Image.Resampling.LANCZOS).save(ROOT / 'android/fastlane/metadata/android/en-US/images/icon.png')
    banner = Image.new('RGB', (732, 412), 'black')
    banner.paste(black.resize((412, 412), Image.Resampling.LANCZOS), (160, 0))
    banner.save(res / 'drawable-xhdpi/ouya_icon.png')
    print('NOUS: transparent desktop, black Android, multi-resolution ICO verified.')


if __name__ == '__main__':
    main(Path(sys.argv[1]))

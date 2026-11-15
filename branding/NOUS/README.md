# Identidade NOUS

| Destino | Arquivo | Fundo |
| --- | --- | --- |
| Android | `new-project-icon-black.png` | Preto opaco |
| Android / referência ICO | `new-project-icon-black.ico` | Preto opaco |
| Desktop | `new-project-icon-transparent.png` | Transparente |
| Windows | `new-project-icon-transparent.ico` | Transparente; 16, 24, 32, 48, 64, 128 e 256 px |

Recursos compilados do desktop ficam em `nous.png`, `nous.ico`, `nous.svg`, `nous.icns` na raiz e nas pastas de assets das interfaces web e macOS. Variações de status usam marcador colorido sobre mesmo emblema. Android mantém fundo preto no ícone normal e adaptativo.

Para regenerar a partir do PNG preto, com Python e Pillow:

```sh
python tools/update_branding.py branding/NOUS/new-project-icon-black.png
```

Gerador verifica transparência, ícones Android opacos e resoluções dos ICOs. SVG contém PNG incorporado; não é reconstrução vetorial do emblema.

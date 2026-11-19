# Identidade NOUS

| Destino | Arquivo | Fundo |
| --- | --- | --- |
| Android | `new-project-icon-black.png` | Preto opaco |
| Android / referência ICO | `new-project-icon-black.ico` | Preto opaco |
| Desktop | `new-project-icon-transparent.png` | Transparente |
| Windows | `new-project-icon-transparent.ico` | Transparente; 16, 24, 32, 48, 64, 128 e 256 px |

Recursos compilados do desktop ficam em `nous.png`, `nous.ico`, `nous.svg`, `nous.icns` na raiz e nas pastas de assets das interfaces web e macOS. Variações de status usam marcador colorido sobre mesmo emblema. Android mantém fundo preto no ícone normal e adaptativo.

Identidade atual: emblema tricolor alinhado. SVG original em `nous-tricolor-alinhado.svg`; nomes legados de assets permanecem para preservar referências. O PNG não quadrado recebe margens transparentes, sem distorcer proporções nem remover cores.

Para regenerar com Python e Pillow:

```sh
python tools/update_branding.py nous.png branding/NOUS/nous-tricolor-alinhado.svg nous.ico
```

Gerador verifica transparência, ícones Android opacos e resoluções dos ICOs gerados. SVG e ICO fornecidos são preservados; variantes de status usam PNG incorporado em SVG.

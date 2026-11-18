# NOUS — pasta de entrega total — 18/11/2026

Esta pasta reúne os binários construídos e a documentação de auditoria/pendências. **A auditoria integrada ainda não foi concluída**; leia `PENDENCIAS.md` antes de tratar esta pasta como release final.

## Conteúdo

| Arquivo | O que é |
| --- | --- |
| `setup.exe` | Instalador Inno Setup 6.7.4, com motor C# e MSI incorporado. Fornecedor **Flűgel**. |
| `NOUS.msi` | MSI usado dentro do setup. Não é necessário manter ao lado do `setup.exe`. |
| `nous-install-engine.exe` | Motor de instalação C# já incorporado ao setup, incluído para inspeção/recuperação. |
| `NOUS-Android-arm64-debug.apk` | APK Android arm64, pacote `com.flugel.nous`, versão 1.0.0 (versionCode 58), build **debug**. |
| `AUDITORIA-E-EVIDENCIAS.md` | Contrato aprovado, decisões e evidências registradas. |
| `PENDENCIAS.md` | O que falta, limites de validação e cuidados de retomada. |
| `VERSAO-BUILD.json` | Branch, commit, versões e hashes. |
| `SHA256SUMS.txt` | Hashes SHA-256 de todos os arquivos acima. |

## Instalação no Windows

1. Faça backup protegido de `C:\Windows\config` e das configurações/pareamentos existentes antes de instalar ou atualizar. A instalação histórica observada usava `InstallLocation=C:\Windows\` e `DisplayVersion=1.0.3-local`.
2. Execute `setup.exe` e autorize a elevação (UAC). O instalador é **não assinado**; não desative proteções do Windows para contornar avisos.
3. Selecione os drivers desejados. O driver de tela virtual e o driver de controle virtual podem exigir confiança em certificado autoassinado; recusar deve continuar permitindo instalar o restante.
4. O progresso mostra etapas reais do motor e ações do MSI. A barra permanece indeterminada enquanto o trabalho ocorre e só completa após o resultado ser verificado. Não há percentual global inventado.
5. Se o Windows exigir reinício, execute novamente o instalador após reiniciar.

O `setup.exe` não precisa do MSI ao lado; a integridade do MSI incorporado é conferida no empacotamento e novamente pelo setup antes de executar o motor.

## Instalação no Android

Pré-requisito: dispositivo/emulador arm64 com depuração USB ou ADB TCP habilitado.

```powershell
adb install -r NOUS-Android-arm64-debug.apk
```

O APK é **debug**: não é assinado para distribuição pública, não cobre outras ABIs e não substitui um build release. Instalar com `-r` preserva dados; não use `adb uninstall` nem limpeza de dados se quiser manter PCs, perfis e preferências.

## Evidências desta entrega

- Build Android concluído com sucesso: **79 testes, zero falhas, zero erros, zero ignorados**.
- APK instalado no MuMu (`127.0.0.1:16384`) com `adb install -r`, preservando PCs e a preferência R3 do menu rápido entre reinstalação e reabertura de Controles.
- Setup recompilado; recurso `Payload.msi` verificado com o mesmo SHA-256 do MSI selecionado.
- Web: 126 testes unitários passaram anteriormente; build aprovado.
- Host: harness HTTP com 11 casos passou; host compilado, mas execução integrada do binário desta entrega não foi validada ponta a ponta.
- Regressões do instalador: 25 migração/metadados, 24 desinstalação, 7 diretório e 387 rollback; limites externos simulados, sem instalação real nesses testes.

## Limitações importantes

- `setup.exe` local está **sem assinatura Authenticode**; um release oficial precisa de assinatura e verificação de origem.
- Instalação, upgrade, rollback, reinício e drivers reais ainda não foram executados nesta entrega.
- Não foram validados streaming no segundo monitor, alça durante sessão, desempenho/Warp, controles Xbox/DualSense físicos, USB/Bluetooth real nem múltiplos controles.
- Capas SteamGridDB, diagnósticos de 30 dias, preferências sincronizadas/mistas, sugestões de apps frequentes e auditoria integral de páginas/opções continuam pendentes.
- GitHub Actions não foi executado/verificado nesta entrega.
- Os binários não entram no Git; a fonte e a documentação estão na branch `feat/nous-ecosystem-audit`.

Consulte `PENDENCIAS.md` para a lista completa e o roteiro de retomada.

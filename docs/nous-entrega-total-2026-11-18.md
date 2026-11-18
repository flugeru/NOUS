# NOUS — pasta de entrega total — 18/11/2026

Revisão `1.0.390.818`: capas padrão 16:9, Steam Big Picture em Launchers, correção do fluxo de instalação do ViGEmBus e ajustes visuais Web. **Não é release final.** A tela azul ocorreu dentro de `ViGEmBus.sys`; o gatilho não foi identificado. Não teste o setup revisado no PC principal; use antes um Windows recuperável com snapshot. Leia `PENDENCIAS.md`.

## Conteúdo

| Arquivo | O que é |
| --- | --- |
| `setup.exe` | Instalador Inno Setup 6.7.4, com motor C#, MSI e ViGEmBus 1.22.0 incorporados. Fornecedor **Flűgel**. FileVersion `1.0.390.818`. |
| `NOUS.msi` | MSI usado dentro do setup. ProductVersion `1.0.390.0`; não é necessário manter ao lado do `setup.exe`. |
| `nous-install-engine.exe` | Motor de instalação C# já incorporado ao setup, incluído para inspeção/recuperação. |
| `NOUS-Android-arm64-debug.apk` | APK Android arm64, pacote `com.flugel.nous`, versão 1.0.0 (versionCode 58), build **debug**. Não foi recompilado nesta rodada. |
| `AUDITORIA-E-EVIDENCIAS.md` | Contrato aprovado, decisões e evidências registradas. |
| `PENDENCIAS.md` | O que falta, limites de validação e cuidados de retomada. |
| `VERSAO-BUILD.json` | Branch, commit, versões e hashes. |
| `SHA256SUMS.txt` | Hashes SHA-256 dos arquivos desta pasta. |

Hashes desta rodada:

| Arquivo | SHA-256 |
| --- | --- |
| `setup.exe` | `94567DA76BCA31EFD9D71B70DC90B8D1541EAF404BB005E482B7A2A4BD5C4045` |
| `NOUS.msi` | `CFCCEC02D49B59D2E964D4B8E2226C16300FC2FB547A58E552668DBEA4F2C7FA` |
| `nous-install-engine.exe` | `A94A74FF098FA80564B7CC550FA1AF4C3AD3F7987B5B437011B2F688FAD19A0A` |
| `NOUS-Android-arm64-debug.apk` | `9413D9E9BBDD90D6F8FCF6B6A53A87EDFCA882C2FF56C0CB7614372FD50D1264` |

## Instalação em Windows de teste

Use uma VM ou máquina recuperável com snapshot. Não execute este procedimento no PC principal enquanto o gatilho da tela azul continuar desconhecido.

1. Faça backup protegido de `C:\Windows\config` e das configurações/pareamentos existentes antes de instalar ou atualizar. A instalação histórica observada usava `InstallLocation=C:\Windows\` e `DisplayVersion=1.0.3-local`.
2. Execute `setup.exe` e autorize a elevação (UAC). O instalador é **não assinado**; não desative proteções do Windows para contornar avisos.
3. Selecione os drivers desejados. ViGEmBus 1.22.0 faz parte do `setup.exe` e é instalado quando necessário. O driver VHF NOUS não está neste MSI. O driver de tela virtual incluído usa certificado autoassinado.
4. O progresso mostra etapas reais do motor e ações do MSI. A barra permanece indeterminada enquanto o trabalho ocorre e só completa após o resultado ser verificado. Não há percentual global inventado.
5. Se o setup indicar reinício, reinicie e confira o controle. Só execute o instalador novamente se ele disser que a instalação foi adiada.

O `setup.exe` não precisa do MSI ao lado. O empacotamento confere o MSI incorporado; antes de executar o motor, o Inno confere o hash do motor que o contém. O MSI avulso não instala ViGEmBus.

## Instalação no Android

Pré-requisito: dispositivo/emulador arm64 com depuração USB ou ADB TCP habilitado.

```powershell
adb install -r NOUS-Android-arm64-debug.apk
```

O APK é **debug**: não é assinado para distribuição pública, não cobre outras ABIs e não substitui um build release. Instalar com `-r` preserva dados; não use `adb uninstall` nem limpeza de dados se quiser manter PCs, perfis e preferências.

## Evidências desta entrega

- Suíte Android executada novamente: **79 testes, zero falhas, zero erros**. O APK não foi recompilado nesta rodada.
- APK instalado no MuMu (`127.0.0.1:16384`) com `adb install -r`, preservando PCs e a preferência R3 do menu rápido entre reinstalação e reabertura de Controles.
- Web: **138 unitários aprovados**; build/typecheck aprovado. Playwright no Edge: 222 aprovados, 16 ignorados, um caso falhou ao abrir contexto e passou isolado.
- Host revisado: build `1.0.390.818`. As quatro capas padrão 640×360 vêm do host, inclusive para Android; não exigem APK novo. Sete testes Web focados passaram. A exibição das novas capas no MuMu ainda não foi validada.
- O host instalado permanece em `1.0.390.687`; respondeu em 47990, com biblioteca, histórico e SteamGridDB verificados. A instância isolada anterior, em 48189/48190, também validou login/CSRF e upload de capas.
- SteamGridDB: busca HTTP 200, grid 920×430, hero 3840×1240, cache 0,389543 s → 0,005745 s, upload real de PNG (71233 bytes) e rejeição de host não permitido.
- Diagnósticos: defaults 30 dias/512 MiB; export JSON com allowlist, `excluded`, `history.available`, `history.degraded` e `history.truncated`.
- Apps frequentes: ranking de 30 dias restrito a apps já existentes.
- Preferências por dispositivo: modos `synced`, `mixed` e `device`.
- Setup recompilado; recurso `Payload.msi` verificado com o mesmo SHA-256 do MSI selecionado.
- Regressões do instalador: 25 migração/metadados, 24 desinstalação, 7 diretório e 387 rollback; limites externos simulados, sem instalação real nesses testes.

## Limitações importantes

- `setup.exe` e `NOUS.msi` locais estão **sem assinatura Authenticode**; um release oficial precisa de assinatura e verificação de origem.
- Setup revisado ainda não executado em instalação real. Upgrade, rollback, progresso em execução e verificação pós-reinício seguem pendentes. O minidump apontou `ViGEmBus.sys` na falha `0x50`; o gatilho permanece desconhecido.
- Não foram validados streaming no segundo monitor, alça durante sessão, desempenho/Warp, controles Xbox/DualSense físicos, USB/Bluetooth real nem múltiplos controles.
- Auditoria integral de páginas/opções e revisão visual completa continuam pendentes.
- CI anterior apresentou falhas em Windows e Android; nova CI ainda não está certificada.
- Os binários não entram no Git; a fonte e a documentação estão na branch `feat/nous-ecosystem-audit`.

Consulte `PENDENCIAS.md` para a lista completa e o roteiro de retomada.

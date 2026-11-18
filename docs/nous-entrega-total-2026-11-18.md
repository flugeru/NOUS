# NOUS — pasta de entrega total — 18/11/2026

Versão pública Windows e Android **1.0.0**. Entrega de teste, não release final. Inclui janela NOUS Desktop no setup, Google opcional e diálogos Material 3 no Android, capas 16:9, Steam Big Picture em Launchers, cache de capas e nova ordem de instalação do ViGEmBus. A tela azul apontou `ViGEmBus.sys`; o gatilho não foi identificado. Não teste o setup revisado no PC principal; use antes um Windows recuperável com snapshot. Leia `PENDENCIAS.md`.

## Conteúdo

| Arquivo | O que é |
| --- | --- |
| `setup.exe` | Instalador Inno Setup 6.7.4, com motor C#, MSI e ViGEmBus 1.22.0 incorporados. Fornecedor **Flűgel**. FileVersion técnica no manifesto. |
| `NOUS.msi` | MSI usado dentro do setup. ProductVersion `1.0.390.0`; não é necessário manter ao lado do `setup.exe`. |
| `nous-install-engine.exe` | Motor de instalação C# já incorporado ao setup, incluído para inspeção/recuperação. |
| `NOUS-Android-arm64-debug.apk` | APK Android arm64, pacote `com.flugel.nous`, versão 1.0.0 (versionCode 60), build **debug**. Na pasta local do usuário, chama-se `nous beta.apk`. |
| `AUDITORIA-E-EVIDENCIAS.md` | Contrato aprovado, decisões e evidências registradas. |
| `PENDENCIAS.md` | O que falta, limites de validação e cuidados de retomada. |
| `VERSAO-BUILD.json` | Branch, commit, versões e hashes. |
| `SHA256SUMS.txt` | Hashes SHA-256 dos arquivos desta pasta. |

Consulte `SHA256SUMS.txt` para verificar esta cópia da entrega.

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

- Android: **82 testes, zero falhas, zero erros**. APK 60 com Material 3 reinstalado no MuMu com `adb install -r`; manteve a primeira data de instalação, a entrada do PC e o login Google. O login não autoriza acesso ao PC. Pareamento local por QR e conexão pela mesma conta seguem pendentes.
- Web: **138 unitários aprovados**; build/typecheck aprovado. Playwright no Edge: 222 aprovados, 16 ignorados, um caso falhou ao abrir contexto e passou isolado.
- Host/setup: build técnico `1.0.390.866`, com `NOUS Desktop.exe` e WebView2 no MSI. Versão pública `1.0.0`; MSI ProductVersion `1.0.390.0`. ZIP portátil inspecionado sem wrapper WebView2. As quatro capas padrão 640×360 vêm do host; cache Android atualizado por `ArtVersion`, sem trocar IDs. Capas reais no MuMu pendentes.
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
- Não foi validado streaming no MuMu pela tela virtual secundária, criada pelo host. Não é necessário monitor físico extra; o principal deve permanecer ativo e inalterado. Alça durante sessão, desempenho/Warp e controles físicos também estão pendentes. HIDMaestro ainda não foi integrado.
- Auditoria integral de páginas/opções e revisão visual completa continuam pendentes.
- CI anterior apresentou falhas em Windows e Android; nova CI ainda não está certificada.
- Os binários não entram no Git; a fonte e a documentação estão na branch `feat/nous-ecosystem-audit`.

Consulte `PENDENCIAS.md` para a lista completa e o roteiro de retomada.

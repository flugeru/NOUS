# NOUS — pasta de entrega total — 18/11/2026

Versão pública Windows e Android **1.0.0**. Entrega de teste, não release final. Inclui janela NOUS Desktop no setup, Google opcional e diálogos Material 3 no Android, capas 16:9, Steam Big Picture em Launchers, cache de capas e nova ordem de instalação do ViGEmBus. A tela azul apontou `ViGEmBus.sys`; o gatilho não foi identificado. Não teste o setup revisado no PC principal; use antes um Windows recuperável com snapshot. Leia `PENDENCIAS.md`.

## Conteúdo

| Arquivo | O que é |
| --- | --- |
| `setup.exe` | Instalador Inno Setup 6.7.4, com motor C#, MSI e ViGEmBus 1.22.0 incorporados. Fornecedor **Flűgel**. FileVersion técnica no manifesto. |
| `NOUS.msi` | MSI usado dentro do setup. ProductVersion `1.0.390.0`; não é necessário manter ao lado do `setup.exe`. |
| `nous-install-engine.exe` | Motor de instalação C# já incorporado ao setup, incluído para inspeção/recuperação. |
| `NOUS-Android-arm64-debug.apk` | APK Android arm64, pacote `com.flugel.nous`, versão 1.0.0 (versionCode 61), build **debug**. |
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

- Android: **82 testes, zero falhas, zero erros**. APK 61 reinstalado no MuMu com `adb install -r`; manteve a primeira data de instalação. O menu Material 3 sobre o vídeo ganhou largura mínima, rolagem e texto multilinha; falta validar numa transmissão. Login Google não autoriza acesso ao PC. QR local e conexão pela mesma conta seguem pendentes; Discord foi adiado.
- Web: **138 unitários e cinco cenários Playwright focados aprovados** nesta revisão. Ajustes alinha Tokens de API e Interface clássica; falha na checagem de versões fica em Manutenção. Transmitir no navegador foi retirado da navegação atual e clássica, e os links antigos retornam ao painel. API e protocolos de streaming preservados.
- Host/setup: build técnico no manifesto, com `NOUS Desktop.exe` e WebView2 no MSI. Versão pública `1.0.0`; MSI ProductVersion `1.0.390.0`. Janela revisada para DPI, moldura escura e sem barra de status do WebView2. As quatro capas padrão 640×360 vêm do host; cache Android atualizado por `ArtVersion`, sem trocar IDs. Capas reais no MuMu pendentes.
- Nenhum setup desta revisão foi executado no PC. Testes anteriores do host em 47990 conferiram biblioteca, histórico e SteamGridDB; instância isolada em 48189/48190 validou login/CSRF e upload de capas.
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

# NOUS — pasta de entrega total — 18/11/2026

Esta pasta reúne os binários construídos e a documentação de auditoria/pendências. **A auditoria integrada ainda não foi concluída**; leia `PENDENCIAS.md` antes de tratar esta pasta como release final.

## Conteúdo

| Arquivo | O que é |
| --- | --- |
| `setup.exe` | Instalador Inno Setup 6.7.4, com motor C# e MSI incorporado. Fornecedor **Flűgel**. FileVersion `1.0.390.687`. |
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
| `setup.exe` | `D2879A201FE35E0DAE88A7DA5172B77D748D0066F499F63127A32AF3DA7902C0` |
| `NOUS.msi` | `6AA258332E41EAEF857CAA4B5BA9FA7A055AA5AD60074D81B3C8A7A659838D69` |
| `nous-install-engine.exe` | `BA9BDB623C8837C382519C9687F771B44B5CE2C87CFE7A2FE289A21D83C44A38` |
| `NOUS-Android-arm64-debug.apk` | `9413D9E9BBDD90D6F8FCF6B6A53A87EDFCA882C2FF56C0CB7614372FD50D1264` |

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

- Build Android anterior concluído com sucesso: **79 testes, zero falhas, zero erros, zero ignorados**. O APK não foi recompilado nesta rodada.
- APK instalado no MuMu (`127.0.0.1:16384`) com `adb install -r`, preservando PCs e a preferência R3 do menu rápido entre reinstalação e reabertura de Controles.
- Web: **138 testes unitários, zero falhas**; build/typecheck aprovado.
- Host: build `1.0.390.687`; instância isolada em 48189/48190 validou `/api/apps`, `/api/history/sessions`, `/api/auth/status`, CSRF/login e os endpoints de capas.
- SteamGridDB: busca HTTP 200, grid 920×430, hero 3840×1240, cache 0,389543 s → 0,005745 s, upload real de PNG (71233 bytes) e rejeição de host não permitido.
- Diagnósticos: defaults 30 dias/512 MiB; export JSON com allowlist, `excluded`, `history.available`, `history.degraded` e `history.truncated`.
- Apps frequentes: ranking de 30 dias restrito a apps já existentes.
- Preferências por dispositivo: modos `synced`, `mixed` e `device`.
- Setup recompilado; recurso `Payload.msi` verificado com o mesmo SHA-256 do MSI selecionado.
- Regressões do instalador: 25 migração/metadados, 24 desinstalação, 7 diretório e 387 rollback; limites externos simulados, sem instalação real nesses testes.

## Limitações importantes

- `setup.exe` e `NOUS.msi` locais estão **sem assinatura Authenticode**; um release oficial precisa de assinatura e verificação de origem.
- Instalação, upgrade, rollback, reinício e drivers reais ainda não foram executados nesta entrega.
- Não foram validados streaming no segundo monitor, alça durante sessão, desempenho/Warp, controles Xbox/DualSense físicos, USB/Bluetooth real nem múltiplos controles.
- Auditoria integral de páginas/opções e revisão visual completa continuam pendentes.
- GitHub Actions não foi executado/verificado nesta entrega.
- Os binários não entram no Git; a fonte e a documentação estão na branch `feat/nous-ecosystem-audit`.

Consulte `PENDENCIAS.md` para a lista completa e o roteiro de retomada.

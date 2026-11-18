# NOUS — entrega parcial e pendências — 18/11/2026

## Estado desta entrega

Escopo reduzido pelo usuário para entregar instalador Inno e APK compilados, registrando trabalho restante. **Não representa auditoria concluída nem versão final integrada.**

Branch: `feat/nous-ecosystem-audit`. Código Android entregue no commit `dbb58fb34`; instalador/CI no commit `c1e564f88`. Este documento será commitado separadamente. Contrato completo e evidências anteriores: `docs/nous-ecosystem-audit.md`.

## Artefatos entregues

Pasta local: `C:\Users\Flugel\Desktop\NOUS-desktop-input-fix\build-local\entrega-2026-11-18`.

| Arquivo | Bytes | SHA-256 |
| --- | ---: | --- |
| `setup.exe` | 80091917 | `1465AE7C027D204C5DD653FD185F4829C1C9A39AEEC4F2CE4A3D6B1638EB00A7` |
| `NOUS-Android-arm64-debug.apk` | 63188991 | `9413D9E9BBDD90D6F8FCF6B6A53A87EDFCA882C2FF56C0CB7614372FD50D1264` |

- Setup recompilado com Inno Setup 6.7.4, fornecedor **Flűgel**, versão numérica do bootstrapper `1.0.390.579`. Contém motor C# e MSI incorporado; não exige MSI ao lado. Empacotamento verificou identidade SHA-256 entre MSI selecionado e recurso `Payload.msi`.
- MSI utilizado: `build-local/cpack_artifacts/NOUS.msi`, SHA-256 `DE3148FCDEAF5F6A133FBA162AB55A5AC5C398AE9BB9EBED690A881571E8AF3C`. MSI foi produzido antes do commit de progresso Inno; contém build host anterior às alterações exclusivamente Android.
- Setup local **sem assinatura Authenticode**; não equivale a release assinada. Não desative proteções do Windows para executá-lo. Drivers beta podem exigir consentimento explícito para confiança no certificado incluído; recusá-lo deve continuar possível.
- APK **debug**, variante `nonRoot_gameDebug`, ABI `arm64-v8a`, pacote `com.flugel.nous`, versão `1.0.0`, versionCode `58`, Android mínimo 7.0/API 24. Não é APK release nem cobre dispositivos de outras ABIs.
- Artefatos ficam fora do Git; commits/push sincronizam fontes e documentação, não estes binários. Cópia deste documento acompanha pasta de entrega.

## Validação realizada

- Último build Android: `BUILD SUCCESSFUL`; relatórios XML somam **79 testes, zero falhas, zero erros, zero ignorados**.
- APK mais recente instalado no MuMu `127.0.0.1:16384` com `adb install -r`, sem limpar dados. PCs e seleção R3 preservados após atualização. Ajustes e Controles abriram; aviso sobre reserva do botão/interceptação pelo Android apareceu.
- Três testes novos exercitam cinco atalhos, repetição, release, preferência inválida, desativação e entrada USB simulada. JNI e serviço SHIELD são limites simulados; isso não comprova controle físico.
- Web: 126 testes passaram na validação anterior; build aprovado. Categoria Launchers de Steam Big Picture persistiu após recarga na interface real usada anteriormente.
- Host: harness HTTP com 11 casos passou, incluindo UUIDs nulos/não-string e rejeição de categorias inválidas. Host atualizado compilou; execução integrada desse binário ainda pendente.
- Instalador: recompilação Inno aprovada; verificações do MSI incorporado aprovadas. Regressões anteriores: 25 migração/metadados, 24 despacho de desinstalação, 7 diretório e 387 rollback. Inventário, MSI e processos externos são simulados nesses testes; nenhuma instalação real foi feita por eles.
- Progresso Inno usa etapas reais do motor e nomes de ações MSI; barra indeterminada até resultado verificado, sem percentual global inventado. Captura e apresentação visual durante instalação real **não foram validadas**.

## Não concluído

### Instalador e host em execução

- Instalação e upgrade reais do setup entregue; preservação efetiva de configurações, credenciais, pareamentos e biblioteca.
- Verificação visual da mesma janela Inno, captura de stdout do motor `winexe`, progresso durante MSI/drivers e mensagens de conclusão/avisos.
- Recuperação/rollback reais diante de falha, reinício necessário e drivers recusados ou bloqueados.
- Execução do host atualizado; validação ponta a ponta `/api/apps`, `/applist`, persistência, filtros de execução e rejeição sem gravação.
- GitHub Actions: execução e resultados ainda não verificados nesta entrega.
- Assinatura e empacotamento de release final; hashes novos obrigatórios após qualquer rebuild.

### Biblioteca e capas

- Integração SteamGridDB público sem chave, com limites, cache, tratamento de indisponibilidade/bloqueio e alternativa manual/local.
- Pesquisa HTTP pública funcionou: GET `/api/public/search/autocomplete` com `term` duplamente codificado; POST `/api/public/search/assets` com `game_id`, `asset_type`, filtros, página e limite. **Não integrado ao produto.**
- Grids observados: 920×430; heroes: 3840×1240. Não são 16:9 nativos. Resolver apresentação/conversão sem deformar nem chamar isso de imagem 16:9 original. Endpoint público sem CORS para host Web exige integração pelo backend.
- Sugestões de apps frequentes e detecção de execução externa com decisão explícita do usuário, sem inclusão automática.
- Auditoria completa da seleção manual de pasta/executável, categorias Desktop/Jogos/Launchers e filtros em ambas as interfaces.

### Android, sessão e identidade visual

- Transmissão real no MuMu usando somente segundo monitor isolado, sem alterar monitor principal.
- Alça lateral durante sessão, recolhimento por inatividade, menu rápido e navegação por controle durante transmissão.
- Xbox/DualSense físicos, USB/Bluetooth, controles divididos e múltiplos controles reais; Android pode interceptar Guide/PS/Xbox antes do aplicativo.
- Performance, latência, estabilidade e Warp em sessão real; MuMu não comprova hardware POCO ou controle físico.
- Auditoria integral de todas as páginas Web e opções Android: backend real, persistência, permissões, acessibilidade, retrato/paisagem e estados vazios/erros.
- Padronização visual restante preservando fundo escuro, ações rosa e navegação ciano.

### Diagnósticos e preferências

- Diagnósticos reais host/cliente integrados por sessão; ausência explícita de dados, histórico local de 30 dias, limite de tamanho, exclusão e exportação manual sem credenciais.
- Preferências por dispositivo com modos local, sincronizado e misto, sem copiar transmissão/controles indiscriminadamente.

## Cuidados para retomada

1. Preserve instalação histórica: registro `HKLM\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\Vibepollo` foi observado com `InstallLocation=C:\Windows\` e `DisplayVersion=1.0.3-local`. Configurações/credenciais históricas em `C:\Windows\config`. Faça backup protegido antes de instalação real; não exponha conteúdo em logs/documentos públicos.
2. Não apague chaves históricas `SOFTWARE\Nonary\Vibepollo\Installer\DriverSelections` nem identidade ARP `Vibepollo` por causa da mudança de fornecedor.
3. Shell atual não elevado; instalação exige UAC. Browser MCP foi desconectado quando barra de depuração foi fechada; não repetir chamadas até reconexão/decisão do usuário. Nenhum controle físico/POCO conectado durante validações registradas.
4. Retome contrato em `docs/nous-ecosystem-audit.md` e respostas em `C:\Users\Flugel\Downloads\NOUS_respostas_revisadas.md`. Trabalhe na branch existente, preserve alterações e mantenha commits/push.

## Comandos já utilizados

```powershell
# Build Android + testes (script local configura JDK/SDK)
powershell.exe -NoProfile -ExecutionPolicy Bypass -File C:\Users\Flugel\AppData\Local\Temp\opencode\nous-android\build-android.ps1
# Executar acima com diretório de trabalho: <repo>\android

# Recompilar setup com MSI selecionado
$env:INNO_SETUP_COMPILER='C:\Users\Flugel\AppData\Local\Temp\opencode\inno6\ISCC.exe'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File packaging\windows\bootstrapper\build_bootstrapper.ps1 -BuildDir build-local -MsiPath build-local\cpack_artifacts\NOUS.msi -OutputName setup.exe -DisableSignPath
```

Scripts temporários e ferramentas locais podem precisar reinstalação em outro computador. Inno CI foi configurado para 6.7.4, mas workflow ainda precisa validação real.

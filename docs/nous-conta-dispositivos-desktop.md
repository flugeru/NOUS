# NOUS — conta, dispositivos e desktop Windows

Adendo ao [escopo existente](nous-ecosystem-audit.md). Não retira trabalho do roadmap nem redesenha telas. Versão pública Windows/Android: `1.0.0`.

## Conta NOUS

- Login opcional com Google e Discord. Conta NOUS tem ID próprio; provedores são vínculos por emissor e identificador estável, não por e-mail. Permitir vincular ambos à mesma conta.
- Se e-mails verificados coincidirem, oferecer mescla, nunca executá-la automaticamente. Reautenticar os dois lados e pedir confirmação explícita; mostrar efeitos, preservar vínculos legítimos e revogar sessões afetadas. Colisão de e-mail nunca concede acesso.
- Authorization Code com PKCE S256 e `state` de uso único; OIDC exige também validação de emissor, audiência, assinatura, validade e `nonce`. Confirmar S256 com cada provedor em ambiente de teste. Usar navegador do sistema para autenticação do provedor; painel NOUS continua na janela própria. Não incorporar páginas de login de terceiros no WebView.
- Backend de conta guarda segredos de servidor; nunca no APK, EXE, interface Web ou logs. Tokens locais em armazenamento protegido: Android Keystore, Windows DPAPI/armazenamento de credenciais e cookies `HttpOnly`/`Secure` no Web, conforme cliente. Revogar sessões, rotacionar tokens de renovação e validar callback exato. Credenciais locais existentes não são migradas para conta em nuvem.

## Vinculação Android e PC

- Mesma conta: o dono confirma uma vez no painel local que o PC pode aceitar instalações da sua conta NOUS. No Android, entrar na mesma conta faz o PC aparecer; tocar em **Conectar** vincula a instalação e abre o acesso sem digitar PIN, ler QR ou aprovar novamente no PC. O host valida uma autorização para aquele PC e aquela instalação antes de aceitá-la. Um e-mail igual entre Google e Discord só sugere vínculo de contas; não substitui essa validação.
- Contas diferentes: QR ou convite com confirmação explícita. Convite curto, de uso único, com expiração e escopo; sem segredo permanente, credenciais locais ou token de provedor no QR.
- Sem conta, na LAN: escolher o PC no Android solicita o pareamento na janela **NOUS Desktop**, sem abrir navegador. O dono confirma no PC; Android lê o QR para concluir sem PIN. O código é gerado localmente, ligado ao host e à chave da instalação, expira em dois minutos e só pode ser usado uma vez. PIN manual continua disponível para clientes atuais e aparelhos sem câmera. Este fluxo é separado de convites entre contas. Host, interface e APK debug 62 compilam; testes da interface e de vínculo do código passaram. Ainda falta validar abertura da janela instalada, leitura com câmera real e pareamento ponta a ponta offline.
- Cada instalação tem identidade e chave próprias. Autorizações vinculam instalação, host e permissões. Host verifica identidade, autorização, escopo e revogação antes do acesso. Revogação de um dispositivo não afeta outros; testar invalidação de acesso novo e sessões ativas. Vinculação não expõe portas nem habilita acesso remoto por padrão.
- Preservar pareamento existente Sunshine/Apollo/Moonlight e uso LAN offline. Cloud indisponível não bloqueia credenciais ou certificados locais já autorizados. Nenhum vínculo por conta pode contornar permissões do host; definir validade e alcance offline de concessões de conta antes de publicar.

## Janela Windows: comparação e decisão

O host C++ serve a mesma Web UI por HTTPS (`src/confighttp.cpp`); `launch_ui()` em `src/entry_handler.cpp` abre navegador, inclusive via bandeja. O MSI atual já cria atalho; Inno empacota o motor/MSI. Serviço precisa permanecer separado do processo visual.

| Opção | Benefício | Custo no NOUS |
| --- | --- | --- |
| WebView2 + WinForms | Reusa Web UI e runtime Evergreen; ferramenta C# de build já existe no instalador; sem Node/Rust na janela | Gerenciar disponibilidade do runtime, TLS local e perfil por usuário; wrapper apenas Windows |
| Tauri | Binário pequeno e multiplataforma, com permissões de ponte configuráveis | Toolchain Rust, configuração IPC e bundler extra; no Windows continua dependendo do WebView2 |
| Electron | Chromium embarcado e ambiente igual entre máquinas | Mais download, atualização própria do Chromium/Node, novo ciclo de empacotamento e isolamento para conteúdo remoto |

**Escolha: WebView2.** Executável separado, não elevado, com ícone NOUS, janela na barra de tarefas, sem abas ou barra de endereço. Sem ponte nativa para JavaScript. Fechar janela não encerra `ApolloService` nem transmissão. Manter URL Web para uso manual e clientes atuais. Avaliar abrir janela pela bandeja e atalhos; host mantém inicialização atual, janela não autoabre por padrão. Incluir no MSI/Inno após validação do runtime, TLS e comportamento offline; falta runtime não pode bloquear o serviço.

Referências: [WebView2 security](https://learn.microsoft.com/en-us/microsoft-edge/webview2/concepts/security), [Tauri Windows installer](https://v2.tauri.app/distribute/windows-installer/), [Electron security](https://www.electronjs.org/docs/latest/tutorial/security), [RFC 8252](https://www.rfc-editor.org/rfc/rfc8252), [RFC 9700](https://www.rfc-editor.org/rfc/rfc9700).

## Diretório de PCs da conta

Firestore `(default)` criado no projeto `nous-game-stream`, região `southamerica-east1` (São Paulo), plano Spark, edição Standard, sem backups pagos. Inicialização em modo produção: nenhuma leitura ou gravação por clientes. A política em `cloud/firestore.rules` foi publicada e relida no console: escopo por UID Firebase e identidade estável do Google, nome e impressão digital pública do host, sem endereço público, token, chave privada ou concessão de pareamento. O diretório não autoriza conexão; o host precisa conferir o vínculo local e a chave da instalação.

Cliente OAuth nativo `NOUS Desktop` registrado no mesmo projeto. Teste no MuMu com APK 63 e sessão preservada: **Ajustes → Conta NOUS → PCs da conta** retornou lista vazia, não erro. Consulta anônima ao diretório retornou HTTP 403. Teste de configuração do endpoint de token, sem credenciais de usuário: Google exigiu `client_secret` pelo cliente Desktop mesmo com `code_verifier`. Não colocar segredo de servidor no aplicativo. Estes testes não concedem acesso ao PC nem demonstram conexão sem PIN.

## Etapas e aceitação

1. Prévia da janela e teste local: HTTPS loopback, origem restrita, popups bloqueados, nada elevado e nenhuma mudança de serviço/driver. Validar render da interface existente, fechamento e mensagens de indisponibilidade. Prévia pode receber certificado público do host por argumento explícito; não adicionar confiança global.
2. Distribuição: validar origem autoritativa e permissões do certificado público do host, renovação, perfil separado, atalhos, bandeja, runtime ausente/offline, MSI upgrade e fechamento durante transmissão em ambiente recuperável. Não instalar runtime ou novo wrapper no PC principal sem validar fluxo.
3. Backend de conta: escolher domínio HTTPS, armazenamento e provisionar apps Google/Discord e callbacks. Testar PKCE, `state`/`nonce`, replay, sessão, revogação e vínculo/mescla com contas de teste. Sem credenciais reais no Git. Configuração e implantação dos provedores ainda pendentes.
4. Clientes: integrar entrada, vínculo, QR/convite e revogação no Windows/Web e Android, sem remover PIN; testar mesma conta, contas diferentes, expiração, replay, revogação e LAN sem internet. Confirmar Steam, biblioteca, controles e upgrades sem regressão.

## Prévia validada

- Build local: `powershell -NoProfile -ExecutionPolicy Bypass -File packaging/windows/desktop/build_desktop_preview.ps1 -OutputDirectory build-local/desktop-preview`. C# x64, WebView2 SDK `1.0.4314.35` com SHA-256 fixo; pacote NuGet verificado com assinatura Microsoft. Usa runtime Evergreen já instalado.
- `NOUS Desktop Preview.exe --self-test <certificado-público.pem>`: checagens de origem e da exceção TLS passaram. A validação TLS normal continua ativa; a exceção para certificado local autoassinado exige origem exata, bytes iguais ao PEM público informado e certificado dentro da validade. Sem alteração na confiança do Windows.
- A janela abriu sem elevação e exibiu o login local real, sem abrir navegador externo. Fechar a janela manteve `ApolloService` em execução e o mesmo PID do host. O certificado público foi copiado para pasta local de teste porque a pasta de credenciais do host exige acesso elevado; não copie a chave privada nem altere permissões dessa pasta. Nenhuma transmissão iniciada; login autenticado, downloads, recursos externos e streaming não validados. Prévia ainda fora do MSI/Inno.
## Revisão da interface — APK debug 61

- Janela Desktop atualizada: DPI por monitor, moldura escura e sem barra de status do WebView2. Consulta de versões permite só a leitura do feed público do NOUS; login externo continua fora da janela. Browser streaming pausado na interface.
- QR LAN e conexão pela mesma conta permanecem pendentes. Discord adiado. Esta revisão não altera a autorização do host nem substitui PIN.

## Build de teste com janela Windows e Google Android

- O MSI/Inno `1.0.390.866` inclui `NOUS Desktop.exe`, dependências e avisos WebView2. Atalho instalado e bandeja passam pelo lançador do desktop. Serviço publica URL e SHA-256 do certificado TLS em HKLM; a janela aceita apenas HTTPS de loopback e usa perfil separado por pin. O build passou e a tabela MSI contém os arquivos esperados. Instalação, renovação TLS, runtime ausente, abertura pela bandeja e fechamento durante transmissão ainda precisam de teste em VM. O ZIP portátil foi recompilado e inspecionado: mantém `NOUS.exe`, sem o wrapper WebView2 nem o desinstalador MSI. O host do PC principal não foi atualizado.
- Firebase `nous-game-stream`: Google ativado, apps Web e Android registrados, SHA-1/SHA-256 do APK debug cadastrados. Para o build Android com Google, use o `google-services.json` atualizado em `android/app/` (ignorado pelo Git; só configuração pública de cliente). Builds sem configuração conservam o modo local. Build 60 (debug arm64): 80 testes sem falhas; instalado com `adb install -r` no MuMu, sem limpar dados. Ajustes → Conta NOUS abriu o consentimento oficial do Google Play Services. O usuário concluiu o login no MuMu e confirmou por captura que o app mostrou a sessão conectada. O PC continuou pedindo pareamento, como esperado.
- Essa prévia só autentica no Firebase. Não concede acesso ao host nem implementa a identidade NOUS, armazenamento de sessão com Keystore, login Desktop/Web, Discord, mescla, conexão sem PIN, QR local, convite entre contas ou revogação por conta. PIN e pareamentos existentes continuam independentes.

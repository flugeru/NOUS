# NOUS — conta, dispositivos e desktop Windows

Adendo ao [escopo existente](nous-ecosystem-audit.md). Não retira trabalho do roadmap nem redesenha telas. Versão pública Windows/Android: `1.0.0`.

## Conta NOUS

- Login opcional com Google e Discord. Conta NOUS tem ID próprio; provedores são vínculos por emissor e identificador estável, não por e-mail. Permitir vincular ambos à mesma conta.
- Se e-mails verificados coincidirem, oferecer mescla, nunca executá-la automaticamente. Reautenticar os dois lados e pedir confirmação explícita; mostrar efeitos, preservar vínculos legítimos e revogar sessões afetadas. Colisão de e-mail nunca concede acesso.
- Authorization Code com PKCE S256 e `state` de uso único; OIDC exige também validação de emissor, audiência, assinatura, validade e `nonce`. Confirmar S256 com cada provedor em ambiente de teste. Usar navegador do sistema para autenticação do provedor; painel NOUS continua na janela própria. Não incorporar páginas de login de terceiros no WebView.
- Backend de conta guarda segredos de servidor; nunca no APK, EXE, interface Web ou logs. Tokens locais em armazenamento protegido: Android Keystore, Windows DPAPI/armazenamento de credenciais e cookies `HttpOnly`/`Secure` no Web, conforme cliente. Revogar sessões, rotacionar tokens de renovação e validar callback exato. Credenciais locais existentes não são migradas para conta em nuvem.

## Vinculação Android e PC

- Mesma conta: oferecer descoberta/vinculação simples, sem acesso automático só por pertencer à conta. Contas diferentes: QR ou convite com confirmação explícita. Convite curto, de uso único, com expiração e escopo; sem segredo permanente, credenciais locais ou token de provedor no QR.
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

## Etapas e aceitação

1. Prévia da janela e teste local: HTTPS loopback, origem restrita, popups bloqueados, nada elevado e nenhuma mudança de serviço/driver. Validar render da interface existente, fechamento e mensagens de indisponibilidade. Prévia pode receber certificado público do host por argumento explícito; não adicionar confiança global.
2. Distribuição: validar origem autoritativa e permissões do certificado público do host, renovação, perfil separado, atalhos, bandeja, runtime ausente/offline, MSI upgrade e fechamento durante transmissão em ambiente recuperável. Não instalar runtime ou novo wrapper no PC principal sem validar fluxo.
3. Backend de conta: escolher domínio HTTPS, armazenamento e provisionar apps Google/Discord e callbacks. Testar PKCE, `state`/`nonce`, replay, sessão, revogação e vínculo/mescla com contas de teste. Sem credenciais reais no Git. Configuração e implantação dos provedores ainda pendentes.
4. Clientes: integrar entrada, vínculo, QR/convite e revogação no Windows/Web e Android, sem remover PIN; testar mesma conta, contas diferentes, expiração, replay, revogação e LAN sem internet. Confirmar Steam, biblioteca, controles e upgrades sem regressão.

## Prévia validada

- Build local: `powershell -NoProfile -ExecutionPolicy Bypass -File packaging/windows/desktop/build_desktop_preview.ps1 -OutputDirectory build-local/desktop-preview`. C# x64, WebView2 SDK `1.0.4314.35` com SHA-256 fixo; pacote NuGet verificado com assinatura Microsoft. Usa runtime Evergreen já instalado.
- `NOUS Desktop Preview.exe --self-test <certificado-público.pem>`: checagens de origem e da exceção TLS passaram. A validação TLS normal continua ativa; a exceção para certificado local autoassinado exige origem exata, bytes iguais ao PEM público informado e certificado dentro da validade. Sem alteração na confiança do Windows.
- A janela abriu sem elevação e exibiu o login local real, sem abrir navegador externo. Fechar a janela manteve `ApolloService` em execução e o mesmo PID do host. O certificado público foi copiado para pasta local de teste porque a pasta de credenciais do host exige acesso elevado; não copie a chave privada nem altere permissões dessa pasta. Nenhuma transmissão iniciada; login autenticado, downloads, recursos externos e streaming não validados. Prévia ainda fora do MSI/Inno.
- Próximo bloco: definir backend/domínio e provisionar Google e Discord com contas de teste. Nenhum login por conta, vínculo por QR ou revogação por conta está ativo.

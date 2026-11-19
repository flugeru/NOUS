# Conta, dispositivos e NOUS Desktop

Contrato público e limites de implementação. Versão do produto: `1.0.0`. A presença de um recurso no roadmap não significa que esteja pronto para produção ou para a Google Play.

## Acesso local e conta são separados

- A senha administrativa do host controla o painel local. Google não substitui essa senha nem concede privilégios de Windows.
- PIN e certificados locais preservam compatibilidade com Sunshine, Apollo e Moonlight. Uso local não exige conta.
- A integração Google é opcional. O diretório de PCs guarda nome e impressão digital pública do certificado do host, não endereço público, token, chave privada ou concessão de streaming.
- O host valida identidade Firebase assinada, conta vinculada, certificado da instalação e prova de posse da chave antes de aceitar uma conexão pela conta. E-mail não é identidade de autorização.
- O dono precisa ativar acesso por conta uma vez no painel local. Depois, uma instalação da mesma conta pode solicitar **Conectar** na LAN sem nova aprovação por PIN.
- O fluxo por conta não reativa um certificado desativado nem recria automaticamente uma instalação removida. Bloquear novos vínculos não revoga pareamentos existentes; eles devem ser tratados na lista de dispositivos.

## Estado da implementação

| Recurso | Estado |
| --- | --- |
| Janela Windows WebView2 separada do serviço | Empacotada em builds de teste. |
| Login Google Android | Testado em emulador com sessão preservada após atualização. |
| Login Google no navegador do sistema | Testado no broker local de desenvolvimento. Não é serviço de conta distribuído pronto. |
| Publicação e leitura do diretório | Testadas com autorização de conta; leitura anônima recusada. |
| Conectar pela conta | Abriu a biblioteca em teste real com certificado já cadastrado, sem duplicação. Primeira instalação nova ainda exige validação ponta a ponta. |
| QR LAN offline | Implementado em builds de teste; leitura com câmera física e fluxo offline completo pendentes. |
| Revogação durante sessão ativa | Pendente de validação integrada. |
| Identidade NOUS independente, Discord, mescla e convites entre contas | Planejados; não concluídos. |
| Sessão Desktop de produção sem broker manual | Pendente. |

Contas com e-mails iguais nunca devem ser mescladas automaticamente. Um fluxo futuro de mescla exige reautenticação e confirmação dos dois lados, preservando permissões e revogações.

## Segurança de login e pareamento

O login nativo usa navegador do sistema, Authorization Code com PKCE S256, `state` de uso único, callback loopback exato e validação OIDC de assinatura, emissor, audiência, tempo e `nonce`. Não inserir login do provedor dentro do WebView nem criar ponte JavaScript para acessar credenciais.

O QR local é ligado ao host, à solicitação e à chave da instalação. Expira em dois minutos e só pode ser usado uma vez. O resgate exige HTTPS com certificado fixado e prova assinada. O QR não deve conter credenciais administrativas nem token permanente. O PIN continua disponível como alternativa.

Indisponibilidade da nuvem não deve invalidar certificados locais já autorizados. Isso não permite criar uma autorização nova sem as verificações exigidas. Antes da distribuição, validar limites offline e exclusão de conta/dados, além de logout e revogação.

## Broker local de desenvolvimento

Esse fluxo é uma prévia, não uma instrução para distribuir segredos no instalador:

1. Mantenha o JSON do cliente OAuth Desktop autorizado em `%LOCALAPPDATA%\NOUS\desktop-oauth.json`. Mantenha a configuração pública Firebase correspondente em `%LOCALAPPDATA%\NOUS\google-services.json`. Ambos ficam fora do Git; nunca publique o conteúdo.
2. Com Node.js disponível, execute `node "<pasta do NOUS>\desktop\desktop-login.mjs"` na sessão normal do usuário. O broker escuta somente em `127.0.0.1:48173`.
3. Entre no painel HTTPS local com a senha administrativa. Em **Seu PC → Conta NOUS**, conclua o login Google, escolha **Ativar acesso por conta** e **Continuar no navegador**. Confirme a publicação conscientemente.
4. No Android da mesma conta, abra **Conta NOUS → PCs da conta**, escolha o PC descoberto na LAN e toque em **Conectar**. Se necessário, adicione o PC localmente antes.

O ticket de ativação é temporário e de uso único. O broker verifica o certificado do endpoint local antes de enviar credenciais. O diretório apenas descobre PCs; não é a autoridade de pareamento. Se a publicação falhar, confira o painel e bloqueie novos vínculos caso não queira manter a autorização.

Configuração pública Firebase e IDs de cliente OAuth não são segredos de servidor, mas devem ter restrições corretas no provedor. Chaves privadas, segredos OAuth e tokens reais não devem aparecer em documentação, logs, APK/AAB ou EXE.

## Janela Windows

`NOUS Desktop.exe` usa WinForms e WebView2 Evergreen para carregar a interface HTTPS do host. É executável separado, sem elevação, abas ou barra de endereço. Fechá-lo não encerra `ApolloService` nem a transmissão. Sem ponte nativa para JavaScript; origem local e exceção TLS ficam restritas ao certificado esperado, sem alterar confiança global do Windows.

A disponibilidade de runtime, renovação TLS, atalhos, abertura pela bandeja, upgrade e comportamento offline precisam integrar a matriz de testes de distribuição. Ausência da janela não pode impedir a execução do serviço.

## Evidências de builds de teste

- Windows `1.0.390.1088`: migração para pasta dedicada preservou identidade TLS, pareamentos, configurações e histórico, com backup protegido e comparação de hashes. Isso não certifica toda migração ou rollback do instalador.
- Android debug 65: sessão Desktop H.264 de aproximadamente seis minutos em emulador; menu rápido, rolagem, navegação com D-pad simulado e encerramento retornaram à biblioteca/lista. Não prova hardware físico nem latência ponta a ponta.
- Android debug 66: alça voltou a recolher após ser reexibida; teste ao vivo confirmou redução da área visível e abertura do menu. Suíte: 85 testes, sem falhas.
- Isolamento `extended_isolated`: rascunho pausado, fora do pacote de teste 66. Compilação e simulações não comprovam preservação do monitor físico ou retirada de saída durante vídeo.

## Antes de publicar

Testar instalação nova e atualização, mesma/outra conta, certificado conflitante, expiração/replay, revogação em sessão ativa, LAN sem internet, câmera real, runtime ausente, permissões, acessibilidade e controles físicos. Não classificar esses testes como concluídos com base apenas em build ou emulador.

[Roadmap e auditoria](nous-ecosystem-audit.md) · [Google Play](maintainers/release.md#android--google-play) · [Segurança](../SECURITY.md)

Referências: [RFC 8252](https://www.rfc-editor.org/rfc/rfc8252), [RFC 9700](https://www.rfc-editor.org/rfc/rfc9700), [WebView2 security](https://learn.microsoft.com/en-us/microsoft-edge/webview2/concepts/security).

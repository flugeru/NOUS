# Manutenção e distribuição do NOUS

Este diretório contém procedimentos públicos para contribuidores e mantenedores. Não deve conter credenciais, configurações de contas pessoais, dumps de produção nem inventário de máquinas privadas. Estar fora do Doxygen **não** torna um arquivo privado: tudo que entra no Git pode ser publicado.

## Guias

- [Release](release.md): revisão, artefatos, assinatura e publicação.
- [Android e Google Play](release.md#android--google-play): checklist obrigatório antes de enviar um AAB.
- [Referência de localização chinesa](chinese-web-ui-localization.md): revisão dos catálogos mantidos.
- [Segurança](../../SECURITY.md): relato privado de vulnerabilidades.

## Antes de enviar um commit

1. Confira `git diff --cached`: nenhuma chave privada, token, senha, serial, nome real de PC ou caminho pessoal.
2. Rode `python tools/test_public_docs.py` na raiz. O check cobre padrões conhecidos, não substitui revisão humana nem scanner do histórico.
3. Mantenha builds, logs, perfis de navegador e material de assinatura fora do Git. `.gitignore` não remove arquivos já versionados nem apaga o histórico.
4. Preserve licenças e créditos upstream. Modelos públicos de hardware, endpoints de serviço e certificados públicos de teste não são automaticamente segredos.

Se houver credencial exposta, revogue/rotacione primeiro. Remover a linha atual não revoga acesso. Reescrever histórico, remover releases ou alterar configurações externas exige coordenação separada.

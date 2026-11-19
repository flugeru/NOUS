# Release NOUS

> Os procedimentos abaixo não certificam uma release. Confirme a política e os workflows ativos na revisão escolhida; não publique nem habilite assinatura automaticamente. Builds locais de debug ou sem assinatura devem permanecer identificados como testes.

## Android — Google Play

Antes do primeiro envio e de cada atualização:

- [ ] Selecione commit limpo, mantenha `applicationId` estável e aumente `versionCode`. Use a variante `nonRoot_gameRelease`, não debug nem a variante root legada.
- [ ] Gere `:app:bundleNonRoot_gameRelease`. O Gradle atual não configura assinatura de release; assine o AAB com a chave de upload protegida e verifique-o. Use Play App Signing; chave de upload e chave distribuída aos usuários têm papéis diferentes.
- [ ] Guarde keystore, senhas e credenciais de publicação fora do Git e dos artefatos públicos. Não forneça conta de serviço ou segredo OAuth dentro do APK/AAB.
- [ ] Confira requisitos atuais de target API no Play Console. O projeto aponta para API 36; isso sozinho não certifica conformidade.
- [ ] Verifique alinhamento ZIP/ELF de 16 KB de **todas** as bibliotecas nativas, inclusive dependências pré-compiladas. `APP_SUPPORT_FLEXIBLE_PAGE_SIZES` não valida o bundle final.
- [ ] Publique política de privacidade verdadeira, pública e acessível também pelo app. Preencha Segurança dos dados com base no comportamento real e nos SDKs, incluindo conta Google, diretório de PCs, diagnósticos, permissões e dados locais. Não declare ausência de coleta sem auditoria.
- [ ] Verifique se o fluxo de conta exige exclusão de conta/dados no app e por URL externa segundo a política vigente. Logout ou revogação de um PC não equivale a exclusão de conta. Esses fluxos e a política de privacidade ainda precisam de validação antes do envio.
- [ ] Revise manifesto mesclado, permissões, backup de credenciais, SDKs e domínios. Cadastre a impressão digital do certificado Play efetivo na integração Google, além dos certificados de desenvolvimento usados separadamente.
- [ ] Teste o AAB pelo canal interno, inclusive splits, instalação limpa/atualização, conta, LAN offline, controles, acessibilidade, retrato/paisagem e dispositivos físicos. Emulador não comprova latência ou hardware.
- [ ] Confira classificação de conteúdo, ficha da loja, suporte, licenças de dependências e entrega do código-fonte correspondente à GPL. Não use marcas nem garantias upstream como se fossem do NOUS.

Referências oficiais: [preparar release](https://developer.android.com/studio/publish/preparing), [assinatura](https://developer.android.com/studio/publish/app-signing), [páginas de 16 KB](https://developer.android.com/guide/practices/page-sizes), [Segurança dos dados](https://support.google.com/googleplay/android-developer/answer/10787469), [exclusão de conta](https://support.google.com/googleplay/android-developer/answer/13327111).

O checklist não representa publicação ou aprovação na Google Play. Não enviar automaticamente pelo CI sem autorização de release.

NOUS releases are built from an existing, v-less semantic-version tag such as `1.19.0-beta.4`
or `1.19.0`. The tagged commit must contain the matching version-scoped release notes file, for
example `release_notes/1.19.0-beta.4.md`.

Linux release candidates are built from `master`. Every branch push builds and validates the
installable Arch/CachyOS package without creating a GitHub Release, updating the signed pacman
repository, or deploying the production Pages site. Download the `build-Archlinux` artifact from
the successful CI run for installation and MOK testing, and tag only a verified `master` commit.

## Prepare the release

1. Update and review the matching file under `release_notes/`.
2. Run the complete local build and test suite appropriate for the changed platforms.
3. Confirm the worktree is clean and the intended release commit is pushed.
4. Create and push the v-less tag:

   ```bash
   git tag 1.19.0-beta.4
   git push origin 1.19.0-beta.4
   ```

Do not create a second `v1.19.0-beta.4` tag. The release workflow adds the `v` prefix only to the
display title.

## Build and publish

After pushing the tag, manually dispatch `.github/workflows/ci.yml` **on that tag ref**,
with the same exact `release_tag` input. The signing gate requires the CI run's tag ref and SHA
both to match; a branch-ref dispatch is not release provenance. The release gate validates the tag, exact release
notes, and source commit, then builds the retained release artifacts. Successful release artifacts
are retained for 14 days.

After the tag build succeeds:

1. Open the **Sign and publish release** workflow.
2. Enter the exact v-less `release_tag`.
3. Leave the recovery run IDs empty during a normal release.
4. Enable `release_draft` for the first production releases so every asset can be inspected before
   publication.
5. Run the workflow and approve the external Windows signing request when required.

The workflow verifies that the retained artifacts came from the exact tagged commit. It publishes
the GitHub Release against the existing tag and includes the Arch package generated by that CI run.

## Verify the published release

- Confirm the GitHub Release is attached to the v-less source tag.
- Confirm its stable/pre-release classification matches the tag suffix.
- Confirm the release notes and every asset version match the tag.
- Download the Arch package on a clean Arch or CachyOS test machine and verify installation,
  executable capabilities, DKMS status, module signature, and managed-display startup.
- Verify the Windows signatures and installer provenance before publishing a draft.

## Publish the Arch Linux repository

For a non-draft release, run **Publish signed Arch repository** with the exact v-less
`release_tag`. The workflow downloads the release's single validated `vibepollo` package, checks
its embedded package name, version, and architecture, signs it, updates the signed `vibepollo`
database on the `arch-repo` branch, and pushes that branch. The explicitly invoked Pages workflow then deploys it at
`https://flugeru.github.io/NOUS/arch/x86_64`.

The `arch-repository` GitHub environment must contain:

- Environment secret `ARCH_REPO_GPG_PRIVATE_KEY`: the ASCII-armored private repository-signing key.
- Environment secret `ARCH_REPO_GPG_PASSPHRASE`: the private key's strong passphrase.
- Environment variable `ARCH_REPO_GPG_FINGERPRINT`: the full uppercase fingerprint with no spaces.

Protect the `arch-repository` environment with required-reviewer approval. Keep an offline backup
of the private key and its revocation certificate. Do not use a personal day-to-day signing key or
the Secure Boot/MOK module-signing key for this repository.

After publication, verify that the Pages deployment succeeds, import the published public key on a
clean Arch or CachyOS machine, add the `[vibepollo]` repository shown on the project site, and run
`pacman -Syu vibepollo`. A normal system upgrade must subsequently discover NOUS updates.

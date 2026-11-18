; Inno Setup 6.7+: UI only. Existing engine owns MSI, drivers and recovery.
#if Ver < EncodeVer(6, 7, 0) || Ver >= EncodeVer(7, 0, 0)
  #error Use Inno Setup 6.7 or later in series 6.
#endif
#ifndef EnginePath
  #error EnginePath must name the qualified MSI bootstrap engine
#endif
[Setup]
AppId=NOUS.Setup
AppName=NOUS
AppVersion={#ProductVersion}
AppPublisher=Flűgel
VersionInfoCompany=Flűgel
VersionInfoVersion={#FileVersion}
VersionInfoProductVersion={#ProductVersion}
DefaultDirName={autopf}\NOUS
UsePreviousAppDir=no
DisableProgramGroupPage=yes
DisableWelcomePage=no
Uninstallable=no
CreateAppDir=no
PrivilegesRequired=admin
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
WizardStyle=modern dark
SetupIconFile=..\..\..\nous.ico
LicenseFile=..\..\..\LICENSE
OutputDir={#ArtifactDir}
OutputBaseFilename={#OutputBase}
Compression=lzma2/fast
SolidCompression=yes
SetupLogging=yes
CloseApplications=no
RestartApplications=no
AllowCancelDuringInstall=no

[Languages]
Name: "brazilianportuguese"; MessagesFile: "compiler:Languages\BrazilianPortuguese.isl"

[Files]
Source: "{#EnginePath}"; DestName: "nous-install-engine.exe"; Flags: dontcopy

[Code]
var
  Drivers: TInputOptionWizardPage;
#if GamepadBundled == "1" && LocalGamepadCertificate == "1"
  GamepadConsent: Boolean;
#endif
  RestartNeeded: Boolean;
  ResultPath: String;

function Selected(Value: Boolean): String;
begin
  if Value then Result := '1' else Result := '0';
end;

procedure InitializeWizard;
var
  InstallDir, Saved: String;
begin
  if RegQueryStringValue(HKLM64, 'SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\Vibepollo', 'InstallLocation', InstallDir) then
    WizardForm.DirEdit.Text := InstallDir;
  Drivers := CreateInputOptionPage(wpSelectDir, 'Drivers e recursos',
    'Escolha recursos para este computador',
    'ViGEmBus 1.22.0 está incluído e será instalado se necessário. Nenhum download adicional é necessário.'#13#10 +
    'NOUS Display Driver usa certificado autoassinado; instalação depende das políticas de segurança do Windows.', False, False);
  Drivers.Add('Instalar ou atualizar driver de tela virtual');
  Drivers.Add('Usar SudoVDA legado em vez de NOUS Display Driver');
  Drivers.Values[0] := True;
  if RegQueryStringValue(HKLM64, 'SOFTWARE\Nonary\Vibepollo\Installer\DriverSelections', 'DisplayEnabled', Saved) then
    Drivers.Values[0] := Saved <> '0';
#if GamepadBundled == "1"
  Drivers.Add('Instalar driver de controle virtual NOUS');
  Drivers.Values[2] := True;
  if RegQueryStringValue(HKLM64, 'SOFTWARE\Nonary\Vibepollo\Installer\DriverSelections', 'GamepadEnabled', Saved) then
    Drivers.Values[2] := Saved <> '0';
#endif
#if UsbIpBundled == "1"
  Drivers.Add('Instalar USB/IP para áudio e resposta tátil do DualSense (opcional)');
#endif
  ResultPath := ExpandConstant('{tmp}\nous-install-result.ini');
end;

function NextButtonClick(CurPageID: Integer): Boolean;
begin
  Result := True;
#if GamepadBundled == "1"
#if LocalGamepadCertificate == "1"
  if (CurPageID = Drivers.ID) and not WizardSilent then begin
    GamepadConsent := False;
    if Drivers.Values[2] then begin
      GamepadConsent := MsgBox('Instalar este driver beta adiciona confiança persistente, para todo o computador, no certificado de assinatura verificado incluído.'#13#10 +
        'Drivers instalados de versão igual ou superior permanecem inalterados.'#13#10 +
        'Impressão digital: {#GamepadThumbprint}'#13#10 +
        'As proteções de segurança do Windows permanecem ativas. Autorizar? Se recusar, o NOUS será instalado sem adicionar este driver.', mbConfirmation, MB_YESNO or MB_DEFBUTTON2) = IDYES;
      Drivers.Values[2] := GamepadConsent;
    end;
  end;
#endif
#endif
end;

function ResultField(const Key: String): String;
var
  Lines: TArrayOfString;
  I: Integer;
begin
  Result := '';
  if LoadStringsFromFile(ResultPath, Lines) then
    for I := 0 to GetArrayLength(Lines) - 1 do
      if Pos(Key + '=', Lines[I]) = 1 then
        Result := Copy(Lines[I], Length(Key) + 2, MaxInt);
end;

procedure EngineOutput(const S: String; const Error, FirstLine: Boolean);
begin
  if Pos('NOUS_STATUS:', S) = 1 then begin
    WizardForm.StatusLabel.Caption := Copy(S, 13, MaxInt);
    WizardForm.StatusLabel.Update;
  end;
end;

procedure CurStepChanged(CurStep: TSetupStep);
var
  Params: String;
  ExitCode: Integer;
begin
  if CurStep <> ssInstall then Exit;
  ExtractTemporaryFile('nous-install-engine.exe');
  if CompareText(GetSHA256OfFile(ExpandConstant('{tmp}\nous-install-engine.exe')), '{#EngineSha256}') <> 0 then
    RaiseException('Falha na verificação de integridade do motor de instalação.');
  Params := '--no-ui --internal-elevated-install --internal-install-path ' + AddQuotes(WizardDirValue) +
    ' --internal-install-display-drivers ' + Selected(Drivers.Values[0]) +
    ' --internal-install-virtual-display-driver ' + Selected(not Drivers.Values[1]) +
    ' --internal-install-save-logs 1 --internal-install-result-path ' + AddQuotes(ResultPath);
#if GamepadBundled == "1"
#if LocalGamepadCertificate == "1"
  Params := Params + ' --internal-install-virtual-gamepad-driver ' + Selected(Drivers.Values[2] and GamepadConsent and not WizardSilent);
  if Drivers.Values[2] and GamepadConsent and not WizardSilent then
    Params := Params + ' --internal-gamepad-certificate-trust 1 --internal-gamepad-consent-thumbprint {#GamepadThumbprint}';
#else
  Params := Params + ' --internal-install-virtual-gamepad-driver ' + Selected(Drivers.Values[2]);
#endif
#else
  Params := Params + ' --internal-install-virtual-gamepad-driver 0';
#endif
#if UsbIpBundled == "1"
#if GamepadBundled == "1"
  Params := Params + ' --internal-install-usbip-transport ' + Selected(Drivers.Values[3]);
#else
  Params := Params + ' --internal-install-usbip-transport ' + Selected(Drivers.Values[2]);
#endif
#endif
  WizardForm.StatusLabel.Caption := 'Instalando NOUS e recursos selecionados. Aguarde…';
  { ponytail: MSI exposes actions, not a reliable overall percentage; keep indeterminate until verified completion. }
  WizardForm.ProgressGauge.Style := npbstMarquee;
  if not ExecAndLogOutput(ExpandConstant('{tmp}\nous-install-engine.exe'), Params, '', SW_HIDE, ewWaitUntilTerminated, ExitCode, @EngineOutput) then
    RaiseException('Não foi possível iniciar instalação. Código: ' + IntToStr(ExitCode));
  Log('Motor de instalação retornou: ' + IntToStr(ExitCode));
  if ExitCode = 1610 then
    RaiseException('Instalação bloqueada. Verifique se a instalação anterior ou o destino usa uma pasta do Windows, a raiz de uma unidade ou uma pasta redirecionada. Não desinstale nem exclua os dados para contornar o bloqueio.');
  if ExitCode = 3017 then
    RaiseException('Instalação adiada: reinicie o Windows e execute este instalador novamente.');
  if (ExitCode <> 0) and (ExitCode <> 3010) then
    RaiseException('Instalação não concluída. Código: ' + IntToStr(ExitCode) + '. Consulte registros em %TEMP%\VibepolloInstaller e na pasta de instalação.');
  if ResultField('ExitCode') <> IntToStr(ExitCode) then
    RaiseException('Motor de instalação não confirmou resultado. Consulte registros antes de tentar novamente.');
  WizardForm.ProgressGauge.Style := npbstNormal;
  WizardForm.ProgressGauge.Position := WizardForm.ProgressGauge.Max;
  RestartNeeded := ExitCode = 3010;
  if ResultField('ComponentFailuresB64') <> '' then
    WizardForm.FinishedLabel.Caption := 'NOUS instalado com avisos: algum componente não foi instalado corretamente. Consulte registros na pasta de instalação antes de usar os drivers.';
end;

function NeedRestart: Boolean;
begin
  Result := RestartNeeded;
end;

using System;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Windows.Forms;
using Microsoft.Win32;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.WinForms;

#if NOUS_PACKAGED
[assembly: System.Reflection.AssemblyProduct("NOUS")]
#else
[assembly: System.Reflection.AssemblyProduct("NOUS Desktop Preview")]
#endif
[assembly: System.Reflection.AssemblyVersion("1.0.0.0")]
[assembly: System.Reflection.AssemblyInformationalVersion("1.0.0")]

internal static class NousDesktop {
  private static bool IsLoopbackHttps(Uri url) {
    return url != null && url.IsAbsoluteUri && url.Scheme == Uri.UriSchemeHttps &&
      (url.Host == "127.0.0.1" || url.Host == "localhost" || url.Host == "[::1]") && url.UserInfo.Length == 0;
  }

  private static bool IsAccountBroker(string raw) {
    const string root = "http://127.0.0.1:48173/";
    return raw == root || (raw.StartsWith(root + "link#ticket=", StringComparison.Ordinal) &&
      raw.Length == root.Length + 12 + 64 && raw.Substring(root.Length + 12).All(c => "0123456789ABCDEF".Contains(c)));
  }

  private static bool IsPublicReleaseFeed(Uri url) {
    return url.Scheme == Uri.UriSchemeHttps && url.IsDefaultPort && url.UserInfo.Length == 0 &&
      url.Host == "api.github.com" && url.AbsolutePath == "/repos/flugeru/NOUS/releases" &&
      (url.Query.Length == 0 || System.Text.RegularExpressions.Regex.IsMatch(url.Query, @"^\?per_page=100&page=(?:[1-9]|10)$"));
  }

  private static bool SameOrigin(Uri origin, string target) {
    Uri url;
    return Uri.TryCreate(target, UriKind.Absolute, out url) && IsLoopbackHttps(url) &&
      origin.Host == url.Host && origin.Port == url.Port;
  }

  private static X509Certificate2 ReadCertificate(string pem) {
    const string begin = "-----BEGIN CERTIFICATE-----";
    const string end = "-----END CERTIFICATE-----";
    pem = (pem ?? "").Trim();
    if (!pem.StartsWith(begin, StringComparison.Ordinal) || !pem.EndsWith(end, StringComparison.Ordinal)) throw new FormatException();
    var der = Convert.FromBase64String(pem.Substring(begin.Length, pem.Length - begin.Length - end.Length));
    if (X509Certificate2.GetCertContentType(der) != X509ContentType.Cert) throw new FormatException();
    return new X509Certificate2(der);
  }

  private static string Fingerprint(X509Certificate2 cert) {
    using (var hash = SHA256.Create()) return BitConverter.ToString(hash.ComputeHash(cert.RawData)).Replace("-", "");
  }

  private static bool MatchesCertificate(string expected, string pem, DateTime now) {
    if (string.IsNullOrEmpty(expected)) return false;
    try {
      using (var cert = ReadCertificate(pem)) {
        return now >= cert.NotBefore && now < cert.NotAfter && String.Equals(Fingerprint(cert), expected, StringComparison.OrdinalIgnoreCase);
      }
    } catch (FormatException) {
      return false;
    } catch (CryptographicException) {
      return false;
    }
  }

  private static bool ParseLocalBinding(string value, out Uri url, out string pin) {
    url = null;
    pin = null;
    var parts = (value ?? "").Split('|');
    if (parts.Length != 2 || parts[1].Length != 64 || !parts[1].All(Uri.IsHexDigit) ||
        !Uri.TryCreate(parts[0], UriKind.Absolute, out url) || !IsLoopbackHttps(url) ||
        url.AbsolutePath != "/" || url.Query.Length != 0 || url.Fragment.Length != 0) return false;
    pin = parts[1];
    return true;
  }

  private static int SelfTest(string certificatePath) {
    var origin = new Uri("https://127.0.0.1:47990/");
    if (!IsAccountBroker("http://127.0.0.1:48173/") || !IsAccountBroker("http://127.0.0.1:48173/link#ticket=" + new string('A', 64)) ||
        IsAccountBroker("http://127.0.0.1:48173/link#ticket=" + new string('G', 64)) || IsAccountBroker("http://127.0.0.1:48173/other") ||
        IsAccountBroker("http://127.0.0.1:48173@other.example/")) return 1;
    if (!SameOrigin(origin, "https://127.0.0.1:47990/pin?q=1")) return 1;
    if (!IsPublicReleaseFeed(new Uri("https://api.github.com/repos/flugeru/NOUS/releases?per_page=100&page=10")) ||
        IsPublicReleaseFeed(new Uri("https://api.github.com/repos/flugeru/NOUS/releases?per_page=100&page=11")) ||
        IsPublicReleaseFeed(new Uri("https://api.github.com/repos/flugeru/NOUS/releases/latest"))) return 1;
    foreach (var blocked in new[] { "http://127.0.0.1:47990/", "https://localhost:47990/", "https://127.0.0.1:443/", "https://127.0.0.1:47990.example.org/", "https://127.0.0.1:47990@www.example.org/", "https://u@127.0.0.1:47990/", "file:///C:/foo", "javascript:alert(1)" }) {
      if (SameOrigin(origin, blocked)) return 1;
    }
    var pem = File.ReadAllText(certificatePath);
    using (var cert = ReadCertificate(pem)) {
      var fingerprint = Fingerprint(cert);
      Uri binding;
      string pin;
      if (!ParseLocalBinding(origin.AbsoluteUri + "|" + fingerprint, out binding, out pin) || binding != origin || pin != fingerprint ||
          ParseLocalBinding("https://example.org/|" + fingerprint, out binding, out pin) ||
          ParseLocalBinding(origin.AbsoluteUri + "|invalid", out binding, out pin) ||
          !MatchesCertificate(fingerprint, pem, cert.NotBefore) || MatchesCertificate(fingerprint, pem, cert.NotAfter) ||
          MatchesCertificate(new string('0', 64), pem, cert.NotBefore) || MatchesCertificate(fingerprint, "invalid", cert.NotBefore) ||
          MatchesCertificate(null, pem, cert.NotBefore)) return 1;
    }
    return 0;
  }

  [System.Runtime.InteropServices.DllImport("user32.dll")]
  private static extern bool SetProcessDpiAwarenessContext(IntPtr context);

  [STAThread]
  private static int Main(string[] args) {
    if (args.Length == 2 && args[0] == "--self-test") return SelfTest(args[1]);
    using (var identity = System.Security.Principal.WindowsIdentity.GetCurrent()) {
      var principal = new System.Security.Principal.WindowsPrincipal(identity);
      if (principal.IsInRole(System.Security.Principal.WindowsBuiltInRole.Administrator)) {
        MessageBox.Show("Abra a janela NOUS sem executar como administrador.", "NOUS");
        return 1;
      }
    }
    // A legacy .NET Framework manifest can report Windows 8 on Windows 10/11.
    try { SetProcessDpiAwarenessContext(new IntPtr(-4)); } catch (EntryPointNotFoundException) { }
    Application.EnableVisualStyles();
    Application.SetCompatibleTextRenderingDefault(false);
    Uri url;
    string pin = null;
#if NOUS_PACKAGED
    SetCurrentProcessExplicitAppUserModelID("Nonary.Vibepollo");
    using (var machine = RegistryKey.OpenBaseKey(RegistryHive.LocalMachine, RegistryView.Registry64))
    using (var key = machine.OpenSubKey(@"SOFTWARE\NOUS\Desktop")) {
      if (!ParseLocalBinding(key == null ? null : key.GetValue("LocalHost") as string, out url, out pin)) {
        MessageBox.Show("O host NOUS ainda não disponibilizou um endereço HTTPS local. Confira se o serviço está ativo e se a interface Web aceita acesso por loopback.", "NOUS");
        return 1;
      }
    }
    Uri target;
    if (args.Length > 1 || (args.Length == 1 && (!args[0].StartsWith("/", StringComparison.Ordinal) ||
        !Uri.TryCreate(url, args[0], out target) || !SameOrigin(url, target.AbsoluteUri)))) return 1;
    if (args.Length == 1) url = new Uri(url, args[0]);
#else
    url = new Uri("https://127.0.0.1:47990/");
    if (args.Length > 2 || (args.Length > 0 && (!Uri.TryCreate(args[0], UriKind.Absolute, out url) || !IsLoopbackHttps(url)))) {
      MessageBox.Show("Use uma URL HTTPS local do NOUS (127.0.0.1, localhost ou [::1]).", "NOUS");
      return 1;
    }
    if (args.Length == 2) {
      try {
        using (var certificate = ReadCertificate(File.ReadAllText(args[1]))) pin = Fingerprint(certificate);
      } catch (Exception) {
        MessageBox.Show("Informe somente o certificado público TLS do host NOUS em formato PEM.", "NOUS");
        return 1;
      }
    }
#endif
    using (var form = new DesktopWindow(url, pin)) Application.Run(form);
    return 0;
  }

#if NOUS_PACKAGED
  [System.Runtime.InteropServices.DllImport("shell32.dll", CharSet = System.Runtime.InteropServices.CharSet.Unicode)]
  private static extern int SetCurrentProcessExplicitAppUserModelID(string appId);
#endif

  private sealed class DesktopWindow : Form {
    private readonly WebView2 web = new WebView2 { Dock = DockStyle.Fill, Visible = false };
    private readonly Label status = new Label { Dock = DockStyle.Fill, TextAlign = ContentAlignment.MiddleCenter };
    private readonly Button retry = new Button { Text = "Tentar novamente", Dock = DockStyle.Bottom, Height = 38, Enabled = false };
    private readonly Uri url;
    private readonly string pinnedCertificate;

    public DesktopWindow(Uri url, string pinnedCertificate) {
      this.url = url;
      this.pinnedCertificate = pinnedCertificate;
      Text = "NOUS";
      Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath);
      Size = new Size(1150, 780);
      MinimumSize = new Size(540, 440);
      AutoScaleMode = AutoScaleMode.Dpi;
      web.DefaultBackgroundColor = Color.FromArgb(20, 22, 30);
      StartPosition = FormStartPosition.CenterScreen;
      BackColor = Color.FromArgb(20, 22, 30);
      ForeColor = Color.White;
      status.Text = "Conectando ao host NOUS…";
      Controls.Add(status);
      Controls.Add(web);
      Controls.Add(retry);
      InitializeOnShow();
    }

    protected override void OnHandleCreated(EventArgs e) {
      base.OnHandleCreated(e);
      int enabled = 1;
      DwmSetWindowAttribute(Handle, 20, ref enabled, sizeof(int));
    }

    [System.Runtime.InteropServices.DllImport("dwmapi.dll")]
    private static extern int DwmSetWindowAttribute(IntPtr window, int attribute, ref int value, int size);

    private void InitializeOnShow() {
      Shown += async (sender, args) => {
        try {
          // WebView2 remembers TLS exceptions in the profile. Never reuse one after a pin change.
#if NOUS_PACKAGED
          const string profileKind = "Desktop";
#else
          const string profileKind = "DesktopPreview";
#endif
          var profile = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "NOUS", profileKind, pinnedCertificate ?? "system-trust");
          var environment = await CoreWebView2Environment.CreateAsync(null, profile);
          if (IsDisposed) return;
          await web.EnsureCoreWebView2Async(environment);
          if (IsDisposed) return;
          var core = web.CoreWebView2;
          core.Settings.AreHostObjectsAllowed = false;
          core.Settings.IsWebMessageEnabled = false;
          core.Settings.AreDevToolsEnabled = false;
          core.Settings.AreDefaultContextMenusEnabled = false;
          core.Settings.AreBrowserAcceleratorKeysEnabled = false;
          core.Settings.IsStatusBarEnabled = false;
          core.NavigationStarting += (s, e) => e.Cancel = !SameOrigin(url, e.Uri);
          core.FrameNavigationStarting += (s, e) => e.Cancel = !SameOrigin(url, e.Uri) && e.Uri != "about:blank";
          core.NewWindowRequested += (s, e) => {
            e.Handled = true;
            if (e.IsUserInitiated && IsAccountBroker(e.Uri)) {
              try { System.Diagnostics.Process.Start(e.Uri); }
              catch { MessageBox.Show("Não foi possível abrir o navegador padrão.", "NOUS"); }
            }
          };
          core.PermissionRequested += (s, e) => e.State = CoreWebView2PermissionState.Deny;
          core.AddWebResourceRequestedFilter("*", CoreWebView2WebResourceContext.All, CoreWebView2WebResourceRequestSourceKinds.All);
          core.WebResourceRequested += (s, e) => {
            Uri request;
            if (Uri.TryCreate(e.Request.Uri, UriKind.Absolute, out request) &&
                (request.Scheme == "blob" || request.Scheme == "data" || SameOrigin(url, e.Request.Uri) ||
                 (e.Request.Method == "GET" && e.ResourceContext == CoreWebView2WebResourceContext.Fetch && IsPublicReleaseFeed(request)))) return;
            e.Response = environment.CreateWebResourceResponse(null, 403, "Blocked by NOUS", "");
          };
          core.ServerCertificateErrorDetected += (s, e) => {
            e.Action = CoreWebView2ServerCertificateErrorAction.Cancel;
            if (SameOrigin(url, e.RequestUri) &&
                (e.ErrorStatus == CoreWebView2WebErrorStatus.CertificateIsInvalid ||
                 e.ErrorStatus == CoreWebView2WebErrorStatus.CertificateCommonNameIsIncorrect) &&
                MatchesCertificate(pinnedCertificate, e.ServerCertificate.ToPemEncoding(), DateTime.Now)) {
              e.Action = CoreWebView2ServerCertificateErrorAction.AlwaysAllow;
            }
          };
          core.NavigationCompleted += (s, e) => {
            if (e.IsSuccess) {
              retry.Visible = false;
              status.Visible = false;
              web.Visible = true;
              web.BringToFront();
            } else if (e.WebErrorStatus != CoreWebView2WebErrorStatus.OperationCanceled) {
              ShowError("Não foi possível abrir o host NOUS. Confira se o serviço está ativo e se o certificado público corresponde ao host.", true);
            }
          };
          retry.Click += (s, e) => core.Navigate(url.AbsoluteUri);
          core.Navigate(url.AbsoluteUri);
        } catch (WebView2RuntimeNotFoundException) {
          ShowError("Runtime Microsoft Edge WebView2 não encontrado. O serviço NOUS não foi alterado.", false);
        } catch (Exception) {
          if (!IsDisposed) ShowError("Falha ao abrir a janela NOUS. Feche-a e tente novamente.", false);
        }
      };
    }

    private void ShowError(string text, bool canRetry) {
      if (IsDisposed) return;
      web.Visible = false;
      status.Text = text;
      status.Visible = true;
      retry.Enabled = canRetry;
      retry.Visible = canRetry;
    }
  }
}

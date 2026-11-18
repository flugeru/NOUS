using System;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Security.Cryptography.X509Certificates;
using System.Windows.Forms;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.WinForms;

[assembly: System.Reflection.AssemblyProduct("NOUS Desktop Preview")]
[assembly: System.Reflection.AssemblyVersion("1.0.0.0")]
[assembly: System.Reflection.AssemblyInformationalVersion("1.0.0")]

internal static class NousDesktop {
  private static bool IsLoopbackHttps(Uri url) {
    return url != null && url.IsAbsoluteUri && url.Scheme == Uri.UriSchemeHttps &&
      (url.Host == "127.0.0.1" || url.Host == "localhost" || url.Host == "[::1]") && url.UserInfo.Length == 0;
  }

  private static bool SameOrigin(Uri origin, string target) {
    Uri url;
    return Uri.TryCreate(target, UriKind.Absolute, out url) && IsLoopbackHttps(url) &&
      origin.Host == url.Host && origin.Port == url.Port;
  }

  private static bool MatchesCertificate(X509Certificate2 expected, string pem, DateTime now) {
    if (expected == null || now < expected.NotBefore || now >= expected.NotAfter || string.IsNullOrEmpty(pem)) return false;
    try {
      var der = Convert.FromBase64String(pem.Replace("-----BEGIN CERTIFICATE-----", "").Replace("-----END CERTIFICATE-----", ""));
      return expected.RawData.SequenceEqual(der);
    } catch (FormatException) {
      return false;
    }
  }

  private static int SelfTest(string certificatePath) {
    var origin = new Uri("https://127.0.0.1:47990/");
    if (!SameOrigin(origin, "https://127.0.0.1:47990/pin?q=1")) return 1;
    foreach (var blocked in new[] { "http://127.0.0.1:47990/", "https://localhost:47990/", "https://127.0.0.1:443/", "https://127.0.0.1:47990.example.org/", "https://127.0.0.1:47990@www.example.org/", "https://u@127.0.0.1:47990/", "file:///C:/foo", "javascript:alert(1)" }) {
      if (SameOrigin(origin, blocked)) return 1;
    }
    using (var cert = new X509Certificate2(certificatePath)) {
      var pem = "-----BEGIN CERTIFICATE-----\r\n" + Convert.ToBase64String(cert.RawData) + "\r\n-----END CERTIFICATE-----";
      if (!MatchesCertificate(cert, pem, cert.NotBefore) || MatchesCertificate(cert, pem, cert.NotAfter) ||
          MatchesCertificate(cert, "AAAA", cert.NotBefore) || MatchesCertificate(cert, "invalid", cert.NotBefore) ||
          MatchesCertificate(null, pem, cert.NotBefore)) return 1;
    }
    return 0;
  }

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
    Application.EnableVisualStyles();
    Application.SetCompatibleTextRenderingDefault(false);
    Uri url = new Uri("https://127.0.0.1:47990/");
    if (args.Length > 2 || (args.Length > 0 && (!Uri.TryCreate(args[0], UriKind.Absolute, out url) || !IsLoopbackHttps(url)))) {
      MessageBox.Show("Use uma URL HTTPS local do NOUS (127.0.0.1, localhost ou [::1]).", "NOUS");
      return 1;
    }
    X509Certificate2 certificate = null;
    if (args.Length == 2) {
      try {
        var pem = File.ReadAllText(args[1]).Trim();
        const string begin = "-----BEGIN CERTIFICATE-----";
        const string end = "-----END CERTIFICATE-----";
        if (!pem.StartsWith(begin, StringComparison.Ordinal) || !pem.EndsWith(end, StringComparison.Ordinal)) throw new FormatException();
        var der = Convert.FromBase64String(pem.Substring(begin.Length, pem.Length - begin.Length - end.Length));
        if (X509Certificate2.GetCertContentType(der) != X509ContentType.Cert) throw new FormatException();
        certificate = new X509Certificate2(der);
      } catch (Exception) {
        MessageBox.Show("Informe somente o certificado público TLS do host NOUS em formato PEM.", "NOUS");
        return 1;
      }
    }
    using (certificate)
    using (var form = new DesktopWindow(url, certificate)) Application.Run(form);
    return 0;
  }

  private sealed class DesktopWindow : Form {
    private readonly WebView2 web = new WebView2 { Dock = DockStyle.Fill, Visible = false };
    private readonly Label status = new Label { Dock = DockStyle.Fill, TextAlign = ContentAlignment.MiddleCenter };
    private readonly Button retry = new Button { Text = "Tentar novamente", Dock = DockStyle.Bottom, Height = 38, Enabled = false };
    private readonly Uri url;
    private readonly X509Certificate2 pinnedCertificate;

    public DesktopWindow(Uri url, X509Certificate2 pinnedCertificate) {
      this.url = url;
      this.pinnedCertificate = pinnedCertificate;
      Text = "NOUS";
      Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath);
      Size = new Size(1150, 780);
      MinimumSize = new Size(540, 440);
      StartPosition = FormStartPosition.CenterScreen;
      BackColor = Color.FromArgb(20, 22, 30);
      ForeColor = Color.White;
      status.Text = "Conectando ao host NOUS…";
      Controls.Add(status);
      Controls.Add(web);
      Controls.Add(retry);
      InitializeOnShow();
    }

    private void InitializeOnShow() {
      Shown += async (sender, args) => {
        try {
          // ponytail: profile dedicated to preview. Installer integration must resolve the host identity before reusing it.
          var profile = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "NOUS", "DesktopPreview");
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
          core.NavigationStarting += (s, e) => e.Cancel = !SameOrigin(url, e.Uri);
          core.FrameNavigationStarting += (s, e) => e.Cancel = !SameOrigin(url, e.Uri) && e.Uri != "about:blank";
          core.NewWindowRequested += (s, e) => e.Handled = true;
          core.PermissionRequested += (s, e) => e.State = CoreWebView2PermissionState.Deny;
          core.AddWebResourceRequestedFilter("*", CoreWebView2WebResourceContext.All, CoreWebView2WebResourceRequestSourceKinds.All);
          core.WebResourceRequested += (s, e) => {
            // Local management preview only. No cross-origin provider sign-in or third-party resources.
            Uri request;
            if (Uri.TryCreate(e.Request.Uri, UriKind.Absolute, out request) &&
                (request.Scheme == "blob" || request.Scheme == "data" || SameOrigin(url, e.Request.Uri))) return;
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

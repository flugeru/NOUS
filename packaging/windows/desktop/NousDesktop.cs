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
    if (!DesktopWindow.TestWindowBounds()) return 1;
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
    private const string WindowKey = @"Software\NOUS\Desktop\Window";

    private static Rectangle FitWindow(Rectangle bounds, Rectangle work) {
      int width = Math.Min(Math.Max(bounds.Width, 800), work.Width);
      int height = Math.Min(Math.Max(bounds.Height, 600), work.Height);
      return new Rectangle(Math.Max(work.Left, Math.Min(bounds.X, work.Right - width)),
        Math.Max(work.Top, Math.Min(bounds.Y, work.Bottom - height)), width, height);
    }

    [System.Runtime.InteropServices.DllImport("shcore.dll")]
    private static extern int GetDpiForMonitor(IntPtr monitor, int type, out uint x, out uint y);
    [System.Runtime.InteropServices.DllImport("user32.dll")]
    private static extern IntPtr MonitorFromPoint(Point point, uint flags);

    private static float ScreenScale(Screen screen) {
      uint x, y;
      try {
        if (GetDpiForMonitor(MonitorFromPoint(new Point(screen.Bounds.Left + 1, screen.Bounds.Top + 1), 2), 0, out x, out y) == 0)
          return x / 96f;
      } catch (DllNotFoundException) { } catch (EntryPointNotFoundException) { }
      return 1f;
    }

    private void RestoreWindow() {
      var screen = Screen.FromPoint(Cursor.Position);
      try {
        using (var key = Registry.CurrentUser.OpenSubKey(WindowKey)) {
          if (key != null) {
            string name = key.GetValue("Monitor") as string;
            screen = Screen.AllScreens.FirstOrDefault(s => s.DeviceName == name) ?? screen;
            float scale = ScreenScale(screen);
            var work = screen.WorkingArea;
            Bounds = FitWindow(new Rectangle(work.Left + (int)(Convert.ToInt32(key.GetValue("X", 0)) * scale),
              work.Top + (int)(Convert.ToInt32(key.GetValue("Y", 0)) * scale),
              (int)(Convert.ToInt32(key.GetValue("Width", 1100)) * scale),
              (int)(Convert.ToInt32(key.GetValue("Height", 760)) * scale)), work);
            MinimumSize = new Size(Math.Min((int)(800 * scale), work.Width), Math.Min((int)(600 * scale), work.Height));
            if (Convert.ToInt32(key.GetValue("Maximized", 0)) == 1) WindowState = FormWindowState.Maximized;
            return;
          }
        }
      } catch (IOException) { } catch (UnauthorizedAccessException) { } catch (System.Security.SecurityException) { }
      catch (FormatException) { } catch (InvalidCastException) { } catch (OverflowException) { }
      var area = screen.WorkingArea;
      float dpi = ScreenScale(screen);
      MinimumSize = new Size(Math.Min((int)(800 * dpi), area.Width), Math.Min((int)(600 * dpi), area.Height));
      int width = Math.Min(area.Width, Math.Max(MinimumSize.Width, (int)(area.Width * .85)));
      int height = Math.Min(area.Height, Math.Max(MinimumSize.Height, (int)(area.Height * .85)));
      Bounds = new Rectangle(area.Left + (area.Width - width) / 2, area.Top + (area.Height - height) / 2, width, height);
    }

    protected override void OnFormClosing(FormClosingEventArgs e) {
      base.OnFormClosing(e);
      if (e.Cancel) return;
      var bounds = WindowState == FormWindowState.Normal ? Bounds : RestoreBounds;
      var screen = Screen.FromRectangle(bounds);
      float scale = ScreenScale(screen);
      try {
        using (var key = Registry.CurrentUser.CreateSubKey(WindowKey)) {
          key.SetValue("Monitor", screen.DeviceName);
          key.SetValue("X", (int)((bounds.X - screen.WorkingArea.X) / scale));
          key.SetValue("Y", (int)((bounds.Y - screen.WorkingArea.Y) / scale));
          key.SetValue("Width", (int)(bounds.Width / scale));
          key.SetValue("Height", (int)(bounds.Height / scale));
          key.SetValue("Maximized", WindowState == FormWindowState.Maximized ? 1 : 0);
        }
      } catch (IOException) { } catch (UnauthorizedAccessException) { } catch (System.Security.SecurityException) { }
    }

    public static bool TestWindowBounds() {
      var area = new Rectangle(-1920, 40, 1920, 1040);
      var bounds = FitWindow(new Rectangle(5000, -2000, 4000, 100), area);
      return area.Contains(bounds) && bounds.Width == 1920 && bounds.Height == 600 &&
        FitWindow(new Rectangle(0, 0, 800, 600), new Rectangle(0, 0, 640, 480)).Size == new Size(640, 480);
    }

    public DesktopWindow(Uri url, string pinnedCertificate) {
      this.url = url;
      this.pinnedCertificate = pinnedCertificate;
      Text = "NOUS";
      Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath);
      AutoScaleMode = AutoScaleMode.Dpi;
      web.DefaultBackgroundColor = Color.FromArgb(20, 22, 30);
      StartPosition = FormStartPosition.Manual;
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
      RestoreWindow();
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
          core.Settings.IsZoomControlEnabled = false;
          core.Settings.IsPinchZoomEnabled = false;
          web.ZoomFactor = 1;
          await core.AddScriptToExecuteOnDocumentCreatedAsync(@"
(() => {
  const editable = node => node instanceof Element && !!node.closest('input, textarea, [contenteditable]:not([contenteditable=false])');
  document.addEventListener('selectstart', event => { if (!editable(event.target)) event.preventDefault(); }, true);
  document.addEventListener('dragstart', event => { if (event.target instanceof Element && event.target.closest('img, svg')) event.preventDefault(); }, true);
  document.addEventListener('contextmenu', event => event.preventDefault(), true);
  document.addEventListener('keydown', event => {
    if (event.ctrlKey && !event.altKey && ['+', '-', '=', '0', 'Add', 'Subtract'].includes(event.key)) event.preventDefault();
  }, true);
  document.addEventListener('wheel', event => { if (event.ctrlKey) event.preventDefault(); }, { capture: true, passive: false });
  document.addEventListener('DOMContentLoaded', () => {
    const style = document.createElement('style');
    style.textContent = 'html, body { user-select: none; -webkit-user-select: none; } input, textarea, [contenteditable]:not([contenteditable=false]) { user-select: text; -webkit-user-select: text; } img { -webkit-user-drag: none; }';
    document.head.append(style);
  }, { once: true });
})();");
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

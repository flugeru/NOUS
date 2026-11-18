using System;
using System.Collections.Generic;
using System.IO;
using System.Reflection;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using PRODUCT_NAMESPACE;

internal static class InstallSuccessRegression {
  static readonly BindingFlags PrivateStatic = BindingFlags.NonPublic | BindingFlags.Static;
  static readonly BindingFlags PrivateInstance = BindingFlags.NonPublic | BindingFlags.Instance;
  static void Check(bool condition, string message) { if (!condition) throw new Exception(message); }
  static T Field<T>(InstallerWindow window, string name) {
    return (T)typeof(InstallerWindow).GetField(name, PrivateInstance).GetValue(window);
  }
  static List<string> Failures(string path) {
    return (List<string>)typeof(InstallerRunner).GetMethod("CollectInstallComponentFailures", PrivateStatic)
      .Invoke(null, new object[] { path, true });
  }
  static bool Reboot(string path) {
    return (bool)typeof(InstallerRunner).GetMethod("InstallLogIndicatesDriverRebootRequired", PrivateStatic)
      .Invoke(null, new object[] { path });
  }
  static void Finish(string directory, string name, int exitCode, string log, string title, bool warning, bool reboot) {
    var result = new InstallerResult {
      Operation = InstallerOperation.Install, ExitCode = exitCode, LogPath = log,
      Message = "Fixture MSI result", ComponentFailures = Failures(log)
    };
    Check(result.Succeeded == (exitCode == 0 || exitCode == 3010), name + ": exit classification");
    Check(result.PartiallySucceeded == (result.Succeeded && warning), name + ": partial success");
    var window = new InstallerWindow(new InstallerArguments());
    Func<Task<InstallerResult>> action = () => Task.FromResult(result);
    var operation = (Task)typeof(InstallerWindow).GetMethod("RunOperationAsync", PrivateInstance)
      .Invoke(window, new object[] { action, "Install", "Installing fixture...", typeof(InstallerWindow).Namespace.Replace("Installer", "") + " installation completed." });
    var actualTitle = Field<TextBlock>(window, "_overlayTitleText").Text;
    Check(actualTitle == title, name + ": final dialog title: " + actualTitle);
    var message = Field<TextBlock>(window, "_overlayMessageText").Text;
    Check(message.Contains("Salve os registros") == (warning || !result.Succeeded), name + ": support report prompt");
    if (result.Succeeded && !warning) {
      Check(Field<TextBlock>(window, "_statusText").Foreground == Field<Brush>(window, "_statusSuccessBrush"), name + ": green success");
      Check(Field<TextBlock>(window, "_statusDetailText").Text.Contains("Reinicie o Windows") == reboot, name + ": reboot detail");
      Check(!message.Contains("falhou") && !message.Contains("avisos"), name + ": no alarming message");
      Check(Field<Button>(window, "_overlaySecondaryButton").Visibility == Visibility.Collapsed, name + ": no report action");
    }
    // Render the actual production completion controls without showing a window.
    var root = (Grid)window.Content;
    window.Content = null;
    root.Measure(new Size(720, 700)); root.Arrange(new Rect(0, 0, 720, 700)); root.UpdateLayout();
    var bitmap = new RenderTargetBitmap(720, 700, 96, 96, PixelFormats.Pbgra32);
    bitmap.Render(root);
    var encoder = new PngBitmapEncoder(); encoder.Frames.Add(BitmapFrame.Create(bitmap));
    using (var stream = File.Create(Path.Combine(directory, name + ".png"))) encoder.Save(stream);
    typeof(InstallerWindow).GetMethod("ResolveOverlay", PrivateInstance).Invoke(window, new object[] { "secondary" });
    operation.GetAwaiter().GetResult();
    Check(window.ProcessExitCode == (result.Succeeded ? 0 : exitCode), name + ": UI exit code");
    window.Close();
    Console.WriteLine(name + ": " + title + ", exit " + exitCode + ", " + result.ComponentFailures.Count + " component diagnostics");
  }
  [STAThread]
  static int Main(string[] args) {
    var directory = args[0];
    var windows = Environment.GetFolderPath(Environment.SpecialFolder.Windows);
    Check(InstallerRunner.IsProtectedInstallRoot(windows) && InstallerRunner.IsProtectedInstallRoot(Path.Combine(windows, @"System32\drivers")) && InstallerRunner.IsProtectedInstallRoot(Path.GetPathRoot(windows)), "Reject OS and drive roots before touching installed products");
    Check(!InstallerRunner.IsProtectedInstallRoot(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "NOUS")), "Accept dedicated local program folder");
    var shouldRun = typeof(InstallerRunner).GetMethod("ShouldRunBundledViGEmBus", PrivateStatic);
    Func<Version, uint, bool, bool, bool> runs = (version, state, present, registered) =>
      (bool)shouldRun.Invoke(null, new object[] { version, state, present, registered });
    Check(runs(null, 0, false, false), "Fresh machine needs bundled setup");
    Check(!runs(new Version(1, 20), 4, true, true) && !runs(new Version(1, 23), 0, false, false), "Preserve existing shared driver");
    Check(!runs(null, 1, false, false) && !runs(null, 0, true, false) && !runs(null, 0, false, true), "Preserve partial installation");
    var complete = typeof(InstallerRunner).GetMethod("CompleteHostInstall", PrivateStatic);
    foreach (var scenario in new[] { new[] { 0, 0, 1, 0, 0 }, new[] { 0, 3010, 1, 3010, 0 }, new[] { 0, 1460, 1, 0, 2 }, new[] { 0, 1167, 1, 0, 1 }, new[] { 3010, 0, 1, 3010, 0 }, new[] { 1603, 0, 1, 1603, 0 }, new[] { 0, 0, 0, 0, 0 } }) {
      var calls = new List<string>();
      var warnings = new List<string>();
      Func<int> driver = () => { calls.Add("driver"); return scenario[1]; };
      Func<bool> host = () => { calls.Add("host"); return true; };
      var code = (int)complete.Invoke(null, new object[] { scenario[0], warnings, driver, host, scenario[2] == 1 });
      var expected = scenario[0] == 1603 ? "" : scenario[2] == 1 && scenario[3] == 0 && scenario[1] != 1460 ? "driver,host" : "driver";
      Check(code == scenario[3] && string.Join(",", calls) == expected && warnings.Count == scenario[4], "Host startup follows driver handling and restart policy");
    }
    var combine = typeof(InstallerRunner).GetMethod("CombineViGEmBusResult", PrivateStatic);
    foreach (var scenario in new[] { new[] { 0, 0, 0, 0 }, new[] { 3010, 0, 3010, 0 }, new[] { 0, 3010, 3010, 0 }, new[] { 0, 1641, 3010, 0 }, new[] { 0, 1603, 0, 1 }, new[] { 3010, 1460, 3010, 1 } }) {
      var warnings = new List<string>();
      var exitCode = (int)combine.Invoke(null, new object[] { scenario[0], scenario[1], warnings });
      Check(exitCode == scenario[2] && warnings.Count == scenario[3], "ViGEmBus result must preserve MSI success and restart state");
    }
    var diagnostic = Path.Combine(directory, "shared-unavailable.log");
    Check(File.ReadAllText(diagnostic).Contains("USBIP_TRANSPORT_INFO"), "Readiness detail remains in the MSI log");
    var saved = (string)typeof(InstallerRunner).GetMethod("PersistInstallLog", PrivateStatic)
      .Invoke(null, new object[] { diagnostic, Path.Combine(directory, "log-copy-fixture"), "install" });
    Check(File.ReadAllText(saved) == File.ReadAllText(diagnostic), "Production saved log preserves the informational diagnostic verbatim");
    foreach (var name in new[] { "shared-unavailable", "shared-ready", "new-ready" }) {
      var path = Path.Combine(directory, name + ".log");
      Check(Failures(path).Count == 0 && !Reboot(path), name + ": clean success classification");
      Finish(directory, name, 0, path, "Concluído", false, false);
    }
    foreach (var name in new[] { "new-not-ready", "new-reboot" }) {
      var path = Path.Combine(directory, name + ".log");
      Check(Failures(path).Count == 0 && Reboot(path), name + ": reboot remains success");
      Finish(directory, name, 3010, path, "Concluído", false, true);
    }
    var failure = Path.Combine(directory, "staging-failed.log");
    Check(Failures(failure).Count > 0, "USB/IP staging error must remain visible");
    Finish(directory, "optional-failure", 0, failure, "Instalação concluída com avisos", true, false);
    Finish(directory, "msi-failure", 1603, Path.Combine(directory, "new-ready.log"), "Falha na instalação", false, false);
    foreach (var marker in new[] { "VIRTUAL_DISPLAY_DRIVER_WARNING", "VIRTUAL_GAMEPAD_DRIVER_WARNING", "CustomAction InstallUsbIpTransport returned actual error code 1603" }) {
      var path = Path.Combine(directory, "required-failure.log");
      File.WriteAllText(path, marker);
      Check(Failures(path).Count > 0, marker + ": genuine component error retained");
    }
    Console.WriteLine("Production log classification and completion UI regressions passed; no installer, service, driver or trust actions executed.");
    return 0;
  }
}

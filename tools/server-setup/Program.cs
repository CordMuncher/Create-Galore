using System;
using System.IO;
using System.Linq;
using System.Net;
using System.Reflection;
using System.Threading;
using System.Windows.Forms;

namespace CreateGalore.ServerSetup
{
    static class Program
    {
        [STAThread]
        static int Main(string[] args)
        {
            // Modrinth, NeoForge's Maven and Adoptium need TLS 1.2+.
            ServicePointManager.SecurityProtocol |= SecurityProtocolType.Tls12;
            var pack = PackBundle.FromAssembly(Assembly.GetExecutingAssembly());
            if (args.Length > 0) return Cli(pack, args);

            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.ThreadException += (s, e) => MessageBox.Show(e.Exception.Message, "Create Galore Server Setup", MessageBoxButtons.OK, MessageBoxIcon.Error);
            Application.Run(new MainForm(pack));
            return 0;
        }

        /// <summary>
        /// No-window mode, for scripts and tests:
        ///   --check                       print what this build installs, exit 0
        ///   --install DIR [--memory GB] [--port N] [--eula] [--elytra] [--clean] [--tf JAR] [--mods FOLDER]
        ///             [--op NAME] [--players A,B] [--log FILE]
        /// </summary>
        static int Cli(PackBundle pack, string[] args)
        {
            TextWriter output = Console.Out;
            Func<string, string> arg = name =>
            {
                int i = Array.IndexOf(args, name);
                return i >= 0 && i + 1 < args.Length ? args[i + 1] : null;
            };
            if (arg("--log") != null) output = TextWriter.Synchronized(new StreamWriter(arg("--log")) { AutoFlush = true });
            try
            {
                if (args[0] == "--check")
                {
                    output.WriteLine("Create Galore " + pack.PackVersion + ", Minecraft " + pack.Minecraft + ", NeoForge " + pack.NeoForge
                        + ", " + pack.Mods.Count + " server mods, Twilight Forest " + pack.TfVersion);
                    using (var z = new System.IO.Compression.ZipArchive(pack.OpenServerFiles()))
                        output.WriteLine(z.Entries.Count + " config and datapack files");
                    return pack.Mods.Count > 0 ? 0 : 1;
                }
                if (args[0] != "--install" || args.Length < 2)
                {
                    output.WriteLine("Usage: --check | --install DIR [--memory GB] [--port N] [--eula] [--elytra] [--clean] [--tf JAR] [--mods FOLDER] [--op NAME] [--players A,B] [--log FILE]");
                    return 2;
                }
                var o = new SetupOptions
                {
                    ServerDir = args[1],
                    AcceptEula = args.Contains("--eula"),
                    Elytra = args.Contains("--elytra"),
                    Clean = args.Contains("--clean"),
                    OpName = arg("--op"),
                    TfJar = arg("--tf"),
                };
                if (arg("--memory") != null) o.MemoryGb = int.Parse(arg("--memory"));
                if (arg("--port") != null) o.Port = int.Parse(arg("--port"));
                if (arg("--players") != null) o.Players = arg("--players").Split(',').ToList();
                if (o.TfJar != null)
                {
                    var tf = TwilightCheck.Check(o.TfJar, pack);
                    output.WriteLine((tf.Ok ? "OK: " : "NOT OK: ") + tf.Message);
                    if (!tf.Ok) return 1;
                }
                if (arg("--mods") != null)
                {
                    o.LocalMods = ModScan.Scan(arg("--mods"), pack);
                    output.WriteLine("Mods folder: " + o.LocalMods.Found.Count + " of " + pack.Mods.Count + " server mods found, " + o.LocalMods.Skipped + " other jars ignored");
                }
                var installer = new ServerInstaller(pack, o, line => output.WriteLine(line), (at, what) => { }, CancellationToken.None);
                installer.RunAsync().GetAwaiter().GetResult();
                return installer.MissingMods.Count == 0 ? 0 : 3;
            }
            catch (Exception e)
            {
                output.WriteLine("ERROR: " + e.Message);
                return 1;
            }
            finally { output.Flush(); }
        }
    }
}

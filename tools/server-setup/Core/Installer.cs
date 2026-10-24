using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Net.Http;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;

namespace CreateGalore.ServerSetup
{
    public sealed class SetupOptions
    {
        public string ServerDir;
        public int MemoryGb = 8;
        public int Port = 25565;
        public bool AcceptEula;
        public bool Elytra;
        public bool Clean;                          // reset every config to the pack's (bug-fixing phase); world kept
        public string OpName;                       // gets op + whitelist
        public List<string> Players = new List<string>(); // whitelist only
        public string TfJar;                        // checked jar to copy into mods/
        public ModScan LocalMods;                   // optional dropped mods folder
        public bool DownloadMissing = true;
    }

    /// <summary>
    /// Builds or updates a Create Galore server folder. Same result as server/install-server.(bat|sh),
    /// which use packwiz-installer: NeoForge server, server-side mods at pinned versions, pack configs,
    /// the shipped server.properties and memory settings. Safe to run again to update.
    /// </summary>
    public sealed class ServerInstaller
    {
        const string InstalledList = ".galore-installed.txt";
        const string ElytraOptional = "config/paxi/optional/create-galore-with-elytra";
        const string ElytraOn = "config/paxi/datapacks/create-galore-with-elytra";

        readonly PackBundle pack;
        readonly SetupOptions o;
        readonly Action<string> log;
        readonly Action<double, string> progress;
        readonly CancellationToken ct;
        readonly HttpClient http;
        string dir, java;

        public List<string> MissingMods = new List<string>();
        public static bool IsWindows { get { return Path.DirectorySeparatorChar == '\\'; } }

        public ServerInstaller(PackBundle pack, SetupOptions options, Action<string> log, Action<double, string> progress, CancellationToken ct)
        {
            this.pack = pack; o = options; this.log = log; this.progress = progress; this.ct = ct;
            http = new HttpClient { Timeout = TimeSpan.FromMinutes(10) };
            http.DefaultRequestHeaders.UserAgent.ParseAdd("CreateGalore-ServerSetup/" + pack.PackVersion + " (github.com/emresahin0906-code/Create-Galore)");
        }

        public async Task RunAsync()
        {
            dir = Path.GetFullPath(o.ServerDir);
            Directory.CreateDirectory(dir);
            log("Server folder: " + dir);

            Step(0.00, "Looking for Java 21");
            java = JavaFinder.Find(dir);
            if (java == null) java = await DownloadJavaAsync();
            log("Using Java: " + java);

            Step(0.08, "Installing NeoForge " + pack.NeoForge);
            await InstallNeoForgeAsync();

            Step(0.25, "Installing mods");
            if (o.Clean) CleanConfigs();
            var previous = ReadInstalledList();
            var installed = new List<string>();
            await InstallModsAsync(installed);

            Step(0.90, "Copying configs and datapacks");
            var hashes = new Dictionary<string, string>();
            InstallPackFiles(installed, previous, hashes);
            RemoveStale(previous.Keys, installed);
            WriteInstalledList(installed, hashes);
            SetElytra();
            InstallTwilightForest();

            Step(0.94, "Writing server settings");
            WriteServerProperties();
            WriteJvmArgs();
            WriteUpdateCheck();
            if (o.AcceptEula) File.WriteAllText(P("eula.txt"), "# Accepted in Create Galore Server Setup (https://aka.ms/MinecraftEULA)\neula=true\n");
            await WritePlayersAsync();
            WriteStartScript();

            Step(1.0, MissingMods.Count == 0 ? "Done" : "Done, but some mods are missing");
            if (MissingMods.Count > 0)
                log("Missing " + MissingMods.Count + " mods: " + string.Join(", ", MissingMods) + ". Run Install again when you're online, or drop a mods folder that has them.");
            if (!File.Exists(P("mods", pack.TfFile)) && !Directory.GetFiles(P("mods"), "twilightforest*.jar").Any())
                log("Twilight Forest is still missing: put " + pack.TfFile + " into " + P("mods") + " before starting.");
            log("Start the server with " + (IsWindows ? "start-server.bat" : "start-server.sh") + " in the server folder.");
        }

        void Step(double at, string what) { ct.ThrowIfCancellationRequested(); progress(at, what); log("== " + what); }
        string P(params string[] parts) { return Path.Combine(new[] { dir }.Concat(parts).ToArray()); }
        string Rel(string relSlash) { return P(relSlash.Split('/')); }

        // ---------- Java ----------

        async Task<string> DownloadJavaAsync()
        {
            if (!IsWindows) throw new InstallException("Java 21 wasn't found. Install it (for example Temurin 21) and run again.");
            log("Java 21 not found, downloading a private copy (Eclipse Temurin 21 JRE) into the server folder");
            string arch = Environment.Is64BitOperatingSystem ? "x64" : "x86";
            string zip = P("java-download.zip");
            await DownloadAsync("https://api.adoptium.net/v3/binary/latest/21/ga/windows/" + arch + "/jre/hotspot/normal/eclipse", zip, null);
            string tmp = P("java-tmp");
            if (Directory.Exists(tmp)) Directory.Delete(tmp, true);
            ZipFile.ExtractToDirectory(zip, tmp);
            File.Delete(zip);
            string top = Directory.GetDirectories(tmp).Single();
            if (Directory.Exists(P("java"))) Directory.Delete(P("java"), true);
            Directory.Move(top, P("java"));
            Directory.Delete(tmp, true);
            string exe = P("java", "bin", "java.exe");
            if (JavaFinder.MajorVersion(exe) < 21) throw new InstallException("The downloaded Java didn't start.");
            return exe;
        }

        // ---------- NeoForge ----------

        async Task InstallNeoForgeAsync()
        {
            string marker = P("libraries", "net", "neoforged", "neoforge", pack.NeoForge);
            if (Directory.Exists(marker) && (File.Exists(P("run.bat")) || File.Exists(P("run.sh"))))
            {
                log("NeoForge " + pack.NeoForge + " is already installed");
                return;
            }
            string jar = P("neoforge-installer.jar");
            await DownloadAsync("https://maven.neoforged.net/releases/net/neoforged/neoforge/" + pack.NeoForge + "/neoforge-" + pack.NeoForge + "-installer.jar", jar, null);
            log("Running the NeoForge installer (downloads Minecraft " + pack.Minecraft + " server files)");
            int code = await RunAsync(java, "-jar \"" + jar + "\" --install-server \"" + dir + "\"", dir);
            if (code != 0) throw new InstallException("The NeoForge installer failed (exit code " + code + "). See the log above.");
            File.Delete(jar);
            if (File.Exists(jar + ".log")) File.Delete(jar + ".log");
            if (File.Exists(P("installer.log"))) File.Delete(P("installer.log"));
        }

        Task<int> RunAsync(string exe, string args, string cwd)
        {
            var tcs = new TaskCompletionSource<int>();
            var p = new Process
            {
                StartInfo = new ProcessStartInfo(exe, args)
                {
                    WorkingDirectory = cwd, UseShellExecute = false, CreateNoWindow = true,
                    RedirectStandardOutput = true, RedirectStandardError = true,
                },
                EnableRaisingEvents = true,
            };
            int lines = 0;
            DataReceivedEventHandler h = (s, e) =>
            {
                if (e.Data == null) return;
                // The installer prints one line per library; keep the log readable.
                if (++lines % 25 == 0 || e.Data.Contains("rror") || e.Data.Contains("Successfully")) log("  " + e.Data);
            };
            p.OutputDataReceived += h;
            p.ErrorDataReceived += h;
            p.Exited += (s, e) => { p.WaitForExit(); tcs.TrySetResult(p.ExitCode); p.Dispose(); };
            p.Start();
            p.BeginOutputReadLine();
            p.BeginErrorReadLine();
            ct.Register(() => { try { p.Kill(); } catch { } });
            return tcs.Task;
        }

        // ---------- Mods ----------

        async Task InstallModsAsync(List<string> installed)
        {
            Directory.CreateDirectory(P("mods"));
            int done = 0, copied = 0, downloaded = 0, kept = 0;
            var gate = new SemaphoreSlim(6);
            var tasks = pack.Mods.Select(async m =>
            {
                await gate.WaitAsync(ct);
                try
                {
                    string dest = Rel(m.Path);
                    Directory.CreateDirectory(Path.GetDirectoryName(dest));
                    string local;
                    if (File.Exists(dest) && Hashing.Sha512File(dest) == m.Sha512) Interlocked.Increment(ref kept);
                    else if (o.LocalMods != null && o.LocalMods.Found.TryGetValue(m, out local))
                    {
                        File.Copy(local, dest, true);
                        Interlocked.Increment(ref copied);
                    }
                    else if (o.DownloadMissing)
                    {
                        try
                        {
                            await DownloadAsync(m.Url, dest, m.Sha512);
                            Interlocked.Increment(ref downloaded);
                        }
                        catch (Exception e) when (!(e is OperationCanceledException))
                        {
                            log("  could not download " + m.Name + ": " + e.Message);
                            lock (MissingMods) MissingMods.Add(m.Name);
                        }
                    }
                    else lock (MissingMods) MissingMods.Add(m.Name);
                    lock (installed) installed.Add(m.Path);
                    int n = Interlocked.Increment(ref done);
                    progress(0.25 + 0.65 * n / pack.Mods.Count, "Installing mods (" + n + " of " + pack.Mods.Count + ")");
                }
                finally { gate.Release(); }
            }).ToList();
            await Task.WhenAll(tasks);
            log(pack.Mods.Count + " server mods: " + downloaded + " downloaded, " + copied + " copied from your folder, " + kept + " already up to date");
        }

        async Task DownloadAsync(string url, string dest, string sha512)
        {
            string part = dest + ".part";
            for (int attempt = 1; ; attempt++)
            {
                try
                {
                    using (var resp = await http.GetAsync(url, HttpCompletionOption.ResponseHeadersRead, ct))
                    {
                        resp.EnsureSuccessStatusCode();
                        using (var src = await resp.Content.ReadAsStreamAsync())
                        using (var dst = File.Create(part))
                            await src.CopyToAsync(dst, 81920, ct);
                    }
                    if (sha512 != null && Hashing.Sha512File(part) != sha512)
                        throw new InstallException("downloaded file doesn't match the pack's checksum");
                    if (File.Exists(dest)) File.Delete(dest);
                    File.Move(part, dest);
                    return;
                }
                catch (Exception) when (attempt < 3 && !ct.IsCancellationRequested)
                {
                    await Task.Delay(1000 * attempt, ct);
                }
                finally
                {
                    if (File.Exists(part)) File.Delete(part);
                }
            }
        }

        // ---------- Configs, datapacks, Field Guide ----------

        /// <summary>
        /// Copies the pack's configs and datapacks. Like packwiz-installer on the client, a file the pack didn't
        /// change since the last install is left alone, so edits made on the server survive an update.
        /// A file the pack changed is replaced (the pack's fix wins).
        /// </summary>
        void InstallPackFiles(List<string> installed, Dictionary<string, string> previous, Dictionary<string, string> hashes)
        {
            int written = 0, kept = 0;
            using (var z = new ZipArchive(pack.OpenServerFiles(), ZipArchiveMode.Read))
            {
                foreach (var e in z.Entries)
                {
                    if (!e.FullName.StartsWith("pack/") || e.FullName.EndsWith("/")) continue;
                    string rel = e.FullName.Substring(5);
                    string dest = Rel(rel);
                    string sha;
                    using (var s = e.Open()) sha = Hashing.Sha256(s);
                    installed.Add(rel);
                    hashes[rel] = sha;
                    string before;
                    if (File.Exists(dest) && previous.TryGetValue(rel, out before) && before == sha)
                    {
                        if (Hashing.Sha256File(dest) != sha) { kept++; log("  kept your edit (unchanged in the pack): " + rel); }
                        continue;
                    }
                    if (File.Exists(dest) && Hashing.Sha256File(dest) == sha) continue;
                    Directory.CreateDirectory(Path.GetDirectoryName(dest));
                    e.ExtractToFile(dest, true);
                    written++;
                }
            }
            log(hashes.Count + " config and datapack files: " + written + " written, " + kept + " kept with your edits, the rest already up to date");
        }

        string ReadTemplate(string name)
        {
            using (var z = new ZipArchive(pack.OpenServerFiles(), ZipArchiveMode.Read))
            using (var r = new StreamReader(z.GetEntry("server/" + name).Open()))
                return r.ReadToEnd();
        }

        /// <summary>Path -> sha256 the pack had for it at the last install ("" for mods and for lists from older versions).</summary>
        Dictionary<string, string> ReadInstalledList()
        {
            var d = new Dictionary<string, string>();
            if (!File.Exists(P(InstalledList))) return d;
            foreach (var l in File.ReadAllLines(P(InstalledList)))
            {
                if (l.Length == 0 || l.StartsWith("#")) continue;
                int tab = l.IndexOf('\t');
                d[tab < 0 ? l : l.Substring(0, tab)] = tab < 0 ? "" : l.Substring(tab + 1);
            }
            return d;
        }

        void WriteInstalledList(List<string> installed, Dictionary<string, string> hashes)
        {
            File.WriteAllText(P(InstalledList), "# Files Create Galore Server Setup installed. Ones dropped from the pack are removed on update.\n"
                + "# Configs carry the pack's checksum, so an update can tell your edits from pack changes.\n"
                + string.Join("\n", installed.Distinct().OrderBy(x => x, StringComparer.Ordinal)
                    .Select(r => hashes.ContainsKey(r) ? r + "\t" + hashes[r] : r)) + "\n");
        }

        /// <summary>Removes files an older pack version installed that the new one no longer has. Never touches other files.</summary>
        void RemoveStale(IEnumerable<string> previous, List<string> installed)
        {
            var now = new HashSet<string>(installed);
            foreach (var rel in previous)
            {
                if (rel.StartsWith("#") || now.Contains(rel) || rel.Contains("..")) continue;
                string f = Rel(rel);
                if (File.Exists(f)) { File.Delete(f); log("  removed (no longer in the pack): " + rel); }
            }
        }

        /// <summary>
        /// --clean: deletes config/, defaultconfigs/ and each world's serverconfig/ so the pack's configs and the mods'
        /// defaults come back on this run. Forgets the config checksums so every pack file is written again.
        /// Mods, NeoForge, the worlds and server.properties, memory, whitelist and ops are kept.
        /// </summary>
        void CleanConfigs()
        {
            log("Resetting every config to the pack's version (the world is kept)");
            foreach (var d in new[] { P("config"), P("defaultconfigs") })
                if (Directory.Exists(d)) { Directory.Delete(d, true); log("  deleted " + Path.GetFileName(d) + "/"); }
            foreach (var w in Directory.GetDirectories(dir))
            {
                string sc = Path.Combine(w, "serverconfig");
                if (File.Exists(Path.Combine(w, "level.dat")) && Directory.Exists(sc)) { Directory.Delete(sc, true); log("  deleted " + Path.GetFileName(w) + "/serverconfig/"); }
            }
            var prev = ReadInstalledList();
            if (prev.Count > 0) WriteInstalledList(prev.Keys.ToList(), new Dictionary<string, string>());
        }

        void SetElytra()
        {
            string on = Rel(ElytraOn);
            if (Directory.Exists(on)) Directory.Delete(on, true);
            if (!o.Elytra) return;
            CopyDir(Rel(ElytraOptional), on);
            log("Elytra switched on (create-galore-with-elytra is in config/paxi/datapacks)");
        }

        public static bool ElytraIsOn(string serverDir)
        {
            return Directory.Exists(Path.Combine(serverDir, "config", "paxi", "datapacks", "create-galore-with-elytra"));
        }

        static void CopyDir(string from, string to)
        {
            foreach (var d in Directory.GetDirectories(from, "*", SearchOption.AllDirectories))
                Directory.CreateDirectory(to + d.Substring(from.Length));
            Directory.CreateDirectory(to);
            foreach (var f in Directory.GetFiles(from, "*", SearchOption.AllDirectories))
                File.Copy(f, to + f.Substring(from.Length), true);
        }

        void InstallTwilightForest()
        {
            string tf = o.TfJar ?? (o.LocalMods != null ? o.LocalMods.TfJar : null);
            if (tf == null) return;
            if (!TwilightCheck.Check(tf, pack).Ok) { log("Skipped " + Path.GetFileName(tf) + ": not Twilight Forest " + pack.TfVersion); return; }
            string dest = P("mods", Path.GetFileName(tf));
            if (string.Equals(Path.GetFullPath(tf), dest, StringComparison.OrdinalIgnoreCase)) return;
            foreach (var old in Directory.GetFiles(P("mods"), "twilightforest*.jar")) File.Delete(old);
            File.Copy(tf, dest, true);
            log("Twilight Forest " + pack.TfVersion + " copied into mods");
        }

        // ---------- Settings ----------

        void WriteServerProperties()
        {
            string path = P("server.properties");
            string text = File.Exists(path) ? File.ReadAllText(path) : ReadTemplate("server.properties");
            text = SetProperty(text, "server-port", o.Port.ToString());
            text = SetProperty(text, "allow-flight", "true"); // airships, jetpacks and contraptions get kicked without it
            text = UpgradeDistances(text);
            File.WriteAllText(path, text);
        }

        // Older installs got view 8 / simulation 6. Raise those to the new defaults,
        // but leave any distance the owner picked themselves alone.
        public static string UpgradeDistances(string text)
        {
            bool oldView = Regex.IsMatch(text, @"^view-distance=8\s*$", RegexOptions.Multiline);
            bool oldSim = Regex.IsMatch(text, @"^simulation-distance=6\s*$", RegexOptions.Multiline);
            if (!oldView || !oldSim) return text;
            text = SetProperty(text, "view-distance", "12");
            return SetProperty(text, "simulation-distance", "10");
        }

        public static string SetProperty(string text, string key, string value)
        {
            var re = new Regex("^" + Regex.Escape(key) + "=.*$", RegexOptions.Multiline);
            if (re.IsMatch(text)) return re.Replace(text, key + "=" + value);
            return text.TrimEnd('\n', '\r') + "\n" + key + "=" + value + "\n";
        }

        void WriteJvmArgs()
        {
            string path = P("user_jvm_args.txt");
            // First run replaces NeoForge's empty default with the pack's tuned flags; later runs only change the memory lines.
            string text = File.Exists(P("user_jvm_args.txt.galore")) && File.Exists(path) ? File.ReadAllText(path) : ReadTemplate("user_jvm_args.txt");
            text = SetMemory(text, o.MemoryGb);
            File.WriteAllText(path, text);
            File.WriteAllText(P("user_jvm_args.txt.galore"), "");
        }

        public static string SetMemory(string text, int gb)
        {
            text = Regex.Replace(text, @"^-Xms\S*", "-Xms" + gb + "G", RegexOptions.Multiline);
            text = Regex.Replace(text, @"^-Xmx\S*", "-Xmx" + gb + "G", RegexOptions.Multiline);
            if (!Regex.IsMatch(text, "^-Xmx", RegexOptions.Multiline)) text = "-Xms" + gb + "G\n-Xmx" + gb + "G\n" + text;
            return text;
        }

        public static int ReadMemory(string serverDir)
        {
            string path = Path.Combine(serverDir, "user_jvm_args.txt");
            if (!File.Exists(path)) return 0;
            var m = Regex.Match(File.ReadAllText(path), @"^-Xmx(\d+)G", RegexOptions.Multiline | RegexOptions.IgnoreCase);
            return m.Success ? int.Parse(m.Groups[1].Value) : 0;
        }

        public static int ReadPort(string serverDir)
        {
            string path = Path.Combine(serverDir, "server.properties");
            if (!File.Exists(path)) return 0;
            var m = Regex.Match(File.ReadAllText(path), @"^server-port=(\d+)", RegexOptions.Multiline);
            return m.Success ? int.Parse(m.Groups[1].Value) : 0;
        }

        async Task WritePlayersAsync()
        {
            var names = new List<string>();
            if (!string.IsNullOrWhiteSpace(o.OpName)) names.Add(o.OpName.Trim());
            names.AddRange(o.Players.Select(p => p.Trim()).Where(p => p.Length > 0));
            if (names.Count == 0) return;
            var whitelist = PlayerList.Load(P("whitelist.json"));
            var ops = PlayerList.Load(P("ops.json"));
            foreach (var name in names.Distinct(StringComparer.OrdinalIgnoreCase))
            {
                var player = await LookupAsync(name);
                if (player == null)
                {
                    log("  couldn't look up " + name + " (check the spelling). Add them later in the server console: whitelist add " + name);
                    continue;
                }
                whitelist.Add(player.Item1, player.Item2, false);
                if (o.OpName != null && string.Equals(name, o.OpName.Trim(), StringComparison.OrdinalIgnoreCase)) ops.Add(player.Item1, player.Item2, true);
                log("  " + player.Item2 + " added to the whitelist" + (ops.Contains(player.Item1) ? " and made operator" : ""));
            }
            whitelist.Save(P("whitelist.json"));
            ops.Save(P("ops.json"));
        }

        /// <summary>Minecraft name to (uuid with dashes, exact name) using Mojang's public profile API.</summary>
        async Task<Tuple<string, string>> LookupAsync(string name)
        {
            if (!Regex.IsMatch(name, "^[A-Za-z0-9_]{1,16}$")) return null;
            try
            {
                using (var resp = await http.GetAsync("https://api.mojang.com/users/profiles/minecraft/" + name, ct))
                {
                    if (!resp.IsSuccessStatusCode) return null;
                    string body = await resp.Content.ReadAsStringAsync();
                    var id = Regex.Match(body, "\"id\"\\s*:\\s*\"([0-9a-fA-F]{32})\"");
                    var exact = Regex.Match(body, "\"name\"\\s*:\\s*\"([A-Za-z0-9_]+)\"");
                    if (!id.Success) return null;
                    string u = id.Groups[1].Value.ToLowerInvariant();
                    u = u.Substring(0, 8) + "-" + u.Substring(8, 4) + "-" + u.Substring(12, 4) + "-" + u.Substring(16, 4) + "-" + u.Substring(20);
                    return Tuple.Create(u, exact.Success ? exact.Groups[1].Value : name);
                }
            }
            catch (Exception e) when (!(e is OperationCanceledException)) { return null; }
        }

        /// <summary>
        /// The update check start-server runs before the server (create-galore-updater.jar --server-start), and the
        /// record of which pack this folder now has, so the check can tell when the release is newer.
        /// When that check is what runs this update, its Java still has the jar open and reads classes from it as it
        /// goes, so the jar must not be rewritten under it (Java lets other programs write to an open jar): the new jar
        /// then goes next to it as create-galore-updater.jar.new, and start-server swaps it in before the next check.
        /// </summary>
        void WriteUpdateCheck()
        {
            File.WriteAllText(P(".galore-pack.txt"), "# Create Galore pack version this server has, for the update check at start\n"
                + "pack=" + pack.PackVersion + "\npack_sha256=" + pack.PackSha256 + "\nneoforge=" + pack.NeoForge + "\ninstaller=app\n");
            byte[] jar;
            using (var s = pack.OpenUpdateChecker())
            using (var m = new MemoryStream()) { s.CopyTo(m); jar = m.ToArray(); }
            string path = P("create-galore-updater.jar"), staged = path + ".new";
            try
            {
                // FileShare.None fails while anything else (the running check) has the jar open.
                using (var f = new FileStream(path, FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None))
                using (var m = new MemoryStream())
                {
                    f.CopyTo(m);
                    if (!m.ToArray().SequenceEqual(jar)) { f.SetLength(0); f.Write(jar, 0, jar.Length); }
                }
                if (File.Exists(staged)) File.Delete(staged);
            }
            catch (Exception e) when (e is IOException || e is UnauthorizedAccessException)
            {
                File.WriteAllBytes(staged, jar);
                log("The update check is running, so its new version is used from the next start");
            }
        }

        void WriteStartScript()
        {
            string args = "@user_jvm_args.txt @libraries/net/neoforged/neoforge/" + pack.NeoForge + "/" + (IsWindows ? "win" : "unix") + "_args.txt";
            if (IsWindows)
            {
                // One bracketed block: cmd reads it whole before running it, so an update that rewrites this file
                // while it runs can't make cmd read half of the new file. The update check exits 0 to start the server.
                File.WriteAllText(P("start-server.bat"),
                    "@echo off\r\nrem Starts the Create Galore server. Type stop in this window to save and shut down.\r\n" +
                    "rem First it checks for a newer pack and asks whether to update (no / yes / back up the world first).\r\n" +
                    "cd /d \"%~dp0\"\r\nset JAVA=java\r\nif exist \"java\\bin\\java.exe\" set JAVA=java\\bin\\java.exe\r\n" +
                    "if exist create-galore-updater.jar.new move /y create-galore-updater.jar.new create-galore-updater.jar >nul\r\n" +
                    "(\r\n" +
                    "  set GALORE_OK=1\r\n" +
                    "  if exist create-galore-updater.jar \"%JAVA%\" -jar create-galore-updater.jar --server-start || set GALORE_OK=\r\n" +
                    "  if defined GALORE_OK \"%JAVA%\" " + args + " %*\r\n" +
                    "  pause\r\n" +
                    "  exit /b\r\n" +
                    ")\r\n");
            }
            else
            {
                string sh = P("start-server.sh");
                File.WriteAllText(sh, "#!/usr/bin/env sh\n# Starts the Create Galore server. Type stop to save and shut down.\n" +
                    "# First it checks for a newer pack and asks whether to update (no / yes / back up the world first).\ncd \"$(dirname \"$0\")\"\n" +
                    "JAVA=java\n[ -x java/bin/java ] && JAVA=java/bin/java\n" +
                    "[ -f create-galore-updater.jar.new ] && mv -f create-galore-updater.jar.new create-galore-updater.jar\n" +
                    "if [ -f create-galore-updater.jar ]; then \"$JAVA\" -jar create-galore-updater.jar --server-start || exit; fi\n" +
                    "exec \"$JAVA\" " + args + " \"$@\"\n");
                try { Process.Start("chmod", "+x \"" + sh + "\"").WaitForExit(); } catch { }
            }
        }
    }

    public sealed class InstallException : Exception
    {
        public InstallException(string message) : base(message) { }
    }

    /// <summary>whitelist.json / ops.json, kept as simple objects so no JSON library is needed.</summary>
    sealed class PlayerList
    {
        readonly List<string> entries = new List<string>();
        readonly HashSet<string> uuids = new HashSet<string>();

        public static PlayerList Load(string path)
        {
            var l = new PlayerList();
            if (!File.Exists(path)) return l;
            foreach (Match m in Regex.Matches(File.ReadAllText(path), @"\{[^{}]*\}"))
            {
                var u = Regex.Match(m.Value, "\"uuid\"\\s*:\\s*\"([^\"]+)\"");
                if (!u.Success || !l.uuids.Add(u.Groups[1].Value.ToLowerInvariant())) continue;
                l.entries.Add(m.Value.Trim());
            }
            return l;
        }

        public bool Contains(string uuid) { return uuids.Contains(uuid); }

        public void Add(string uuid, string name, bool op)
        {
            if (!uuids.Add(uuid)) return;
            entries.Add(op
                ? "{\n    \"uuid\": \"" + uuid + "\",\n    \"name\": \"" + name + "\",\n    \"level\": 4,\n    \"bypassesPlayerLimit\": false\n  }"
                : "{\n    \"uuid\": \"" + uuid + "\",\n    \"name\": \"" + name + "\"\n  }");
        }

        public void Save(string path)
        {
            File.WriteAllText(path, entries.Count == 0 ? "[]\n" : "[\n  " + string.Join(",\n  ", entries) + "\n]\n");
        }
    }

    public static class JavaFinder
    {
        /// <summary>Finds a Java 21+ to use: the server folder's private copy, JAVA_HOME, PATH, then common install folders.</summary>
        public static string Find(string serverDir)
        {
            string exe = ServerInstaller.IsWindows ? "java.exe" : "java";
            var candidates = new List<string> { Path.Combine(serverDir, "java", "bin", exe) };
            string home = Environment.GetEnvironmentVariable("JAVA_HOME");
            if (!string.IsNullOrEmpty(home)) candidates.Add(Path.Combine(home, "bin", exe));
            foreach (var d in (Environment.GetEnvironmentVariable("PATH") ?? "").Split(Path.PathSeparator))
                if (d.Length > 0) candidates.Add(Path.Combine(d.Trim('"'), exe));
            if (ServerInstaller.IsWindows)
            {
                foreach (var root in new[] { Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), @"C:\Program Files" }.Distinct())
                    foreach (var vendor in new[] { "Eclipse Adoptium", "Microsoft", "Java", "Zulu", "BellSoft", "Amazon Corretto" })
                    {
                        string v = Path.Combine(root, vendor);
                        if (!Directory.Exists(v)) continue;
                        foreach (var jdk in Directory.GetDirectories(v).OrderByDescending(x => x))
                            candidates.Add(Path.Combine(jdk, "bin", exe));
                    }
            }
            foreach (var c in candidates.Distinct())
                if (File.Exists(c) && MajorVersion(c) >= 21) return c;
            return null;
        }

        public static int MajorVersion(string java)
        {
            try
            {
                var psi = new ProcessStartInfo(java, "-version") { UseShellExecute = false, CreateNoWindow = true, RedirectStandardError = true, RedirectStandardOutput = true };
                using (var p = Process.Start(psi))
                {
                    string err = p.StandardError.ReadToEnd() + p.StandardOutput.ReadToEnd();
                    p.WaitForExit(15000);
                    var m = Regex.Match(err, "version \"(\\d+)(?:\\.(\\d+))?");
                    if (!m.Success) return 0;
                    int major = int.Parse(m.Groups[1].Value);
                    return major == 1 && m.Groups[2].Success ? int.Parse(m.Groups[2].Value) : major;
                }
            }
            catch { return 0; }
        }
    }
}

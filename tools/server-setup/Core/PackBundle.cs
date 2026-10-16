using System;
using System.Collections.Generic;
using System.IO;
using System.Reflection;
using System.Security.Cryptography;
using System.Text;

namespace CreateGalore.ServerSetup
{
    /// <summary>One server-side mod from pack/, as listed in server-manifest.tsv.</summary>
    public sealed class ModEntry
    {
        public string Path;    // e.g. mods/create-1.21.1-6.0.10.jar
        public string Sha512;
        public string Url;
        public string Name;
        public string FileName { get { return System.IO.Path.GetFileName(Path); } }
    }

    /// <summary>
    /// The pack data the app installs: built from pack/ by tools/build_server_bundle.py and
    /// embedded in the exe at build time, so each app build matches exactly one pack version.
    /// </summary>
    public sealed class PackBundle
    {
        public string PackVersion, PackSha256, Minecraft, NeoForge, TfFile, TfSha256, TfVersion;
        public List<ModEntry> Mods = new List<ModEntry>();
        public Dictionary<string, ModEntry> BySha512 = new Dictionary<string, ModEntry>(StringComparer.OrdinalIgnoreCase);
        Func<Stream> openFiles, openChecker;

        public Stream OpenServerFiles() { return openFiles(); }

        /// <summary>create-galore-updater.jar, which start-server runs to ask about pack updates.</summary>
        public Stream OpenUpdateChecker() { return openChecker(); }

        public static PackBundle FromAssembly(Assembly asm)
        {
            return Load(name => asm.GetManifestResourceStream("bundle." + name)
                ?? throw new InvalidOperationException("This build is missing bundle." + name));
        }

        public static PackBundle FromFolder(string dir)
        {
            return Load(name => File.OpenRead(System.IO.Path.Combine(dir, name)));
        }

        static PackBundle Load(Func<string, Stream> open)
        {
            var b = new PackBundle { openFiles = () => open("server-files.zip"), openChecker = () => open("create-galore-updater.jar") };
            foreach (var line in ReadLines(open("pack-info.txt")))
            {
                int eq = line.IndexOf('=');
                if (eq < 0) continue;
                string k = line.Substring(0, eq), v = line.Substring(eq + 1);
                switch (k)
                {
                    case "pack": b.PackVersion = v; break;
                    case "pack_sha256": b.PackSha256 = v; break;
                    case "minecraft": b.Minecraft = v; break;
                    case "neoforge": b.NeoForge = v; break;
                    case "tf_file": b.TfFile = v; break;
                    case "tf_sha256": b.TfSha256 = v; break;
                    case "tf_version": b.TfVersion = v; break;
                }
            }
            foreach (var line in ReadLines(open("server-manifest.tsv")))
            {
                var f = line.Split('\t');
                if (f.Length < 4) continue;
                var m = new ModEntry { Path = f[0], Sha512 = f[1], Url = f[2], Name = f[3] };
                b.Mods.Add(m);
                b.BySha512[m.Sha512] = m;
            }
            return b;
        }

        static IEnumerable<string> ReadLines(Stream s)
        {
            using (var r = new StreamReader(s, Encoding.UTF8))
            {
                string line;
                while ((line = r.ReadLine()) != null)
                    if (line.Length > 0) yield return line;
            }
        }
    }

    public static class Hashing
    {
        public static string Sha512File(string path)
        {
            using (var h = SHA512.Create())
            using (var s = File.OpenRead(path)) return Hex(h.ComputeHash(s));
        }

        public static string Sha256File(string path)
        {
            using (var h = SHA256.Create())
            using (var s = File.OpenRead(path)) return Hex(h.ComputeHash(s));
        }

        public static string Sha256(Stream s)
        {
            using (var h = SHA256.Create()) return Hex(h.ComputeHash(s));
        }

        public static string Hex(byte[] bytes)
        {
            var sb = new StringBuilder(bytes.Length * 2);
            foreach (var b in bytes) sb.Append(b.ToString("x2"));
            return sb.ToString();
        }
    }
}

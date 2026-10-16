using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Text.RegularExpressions;

namespace CreateGalore.ServerSetup
{
    public enum TfStatus { Exact, SameVersion, WrongVersion, NotTwilightForest, Unreadable }

    public sealed class TfResult
    {
        public TfStatus Status;
        public string Message;
        public bool Ok { get { return Status == TfStatus.Exact || Status == TfStatus.SameVersion; } }
    }

    public static class TwilightCheck
    {
        /// <summary>Exact hash match, or failing that the version inside the jar's mods.toml.</summary>
        public static TfResult Check(string jar, PackBundle b)
        {
            try
            {
                if (string.Equals(Hashing.Sha256File(jar), b.TfSha256, StringComparison.OrdinalIgnoreCase))
                    return new TfResult { Status = TfStatus.Exact, Message = "Twilight Forest " + b.TfVersion + ", the exact file the pack was tested with" };
                string version = ReadModVersion(jar, "twilightforest");
                if (version == null)
                    return new TfResult { Status = TfStatus.NotTwilightForest, Message = Path.GetFileName(jar) + " isn't Twilight Forest" };
                if (version == b.TfVersion)
                    return new TfResult { Status = TfStatus.SameVersion, Message = "Twilight Forest " + version + " (right version)" };
                return new TfResult { Status = TfStatus.WrongVersion, Message = "This is Twilight Forest " + version + ". The pack needs " + b.TfVersion + " (" + b.TfFile + ")" };
            }
            catch (Exception e)
            {
                return new TfResult { Status = TfStatus.Unreadable, Message = "Can't read " + Path.GetFileName(jar) + ": " + e.Message };
            }
        }

        /// <summary>Returns the version of modId from META-INF/neoforge.mods.toml, or null if the jar isn't that mod.</summary>
        public static string ReadModVersion(string jar, string modId)
        {
            using (var z = ZipFile.OpenRead(jar))
            {
                var e = z.GetEntry("META-INF/neoforge.mods.toml");
                if (e == null) return null;
                string toml;
                using (var r = new StreamReader(e.Open())) toml = r.ReadToEnd();
                // [[mods]] blocks: modId = "x" ... version = "y"
                foreach (var block in Regex.Split(toml, @"^\s*\[\[", RegexOptions.Multiline))
                {
                    if (!block.StartsWith("mods]]")) continue;
                    var id = Regex.Match(block, "^\\s*modId\\s*=\\s*\"([^\"]+)\"", RegexOptions.Multiline);
                    if (!id.Success || id.Groups[1].Value != modId) continue;
                    var ver = Regex.Match(block, "^\\s*version\\s*=\\s*\"([^\"]+)\"", RegexOptions.Multiline);
                    return ver.Success ? ver.Groups[1].Value : "";
                }
                return null;
            }
        }
    }

    /// <summary>What a dropped mods folder holds, matched against the server list by SHA-512.</summary>
    public sealed class ModScan
    {
        public string Folder;
        public Dictionary<ModEntry, string> Found = new Dictionary<ModEntry, string>();
        public int Skipped;          // client-only mods, other versions, anything not on the server list
        public string TfJar;         // a Twilight Forest jar seen in the folder, if any
        public int Missing(PackBundle b) { return b.Mods.Count - Found.Count; }

        public static ModScan Scan(string folder, PackBundle b, Action<int, int> progress = null)
        {
            var scan = new ModScan { Folder = folder };
            var jars = Directory.GetFiles(folder, "*.jar", SearchOption.TopDirectoryOnly);
            for (int i = 0; i < jars.Length; i++)
            {
                if (progress != null) progress(i, jars.Length);
                string jar = jars[i];
                ModEntry m;
                if (b.BySha512.TryGetValue(Hashing.Sha512File(jar), out m)) scan.Found[m] = jar;
                else if (Path.GetFileName(jar).StartsWith("twilightforest", StringComparison.OrdinalIgnoreCase)) scan.TfJar = jar;
                else scan.Skipped++;
            }
            if (progress != null) progress(jars.Length, jars.Length);
            return scan;
        }
    }
}

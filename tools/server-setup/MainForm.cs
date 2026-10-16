using System;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.IO;
using System.Linq;
using System.Runtime.InteropServices;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace CreateGalore.ServerSetup
{
    sealed class MainForm : Form
    {
        static readonly Color Brass = Color.FromArgb(184, 124, 52);
        static readonly Color Dark = Color.FromArgb(38, 40, 46);
        static readonly Color Good = Color.FromArgb(32, 140, 72);
        static readonly Color Bad = Color.FromArgb(196, 52, 52);
        static readonly Color Muted = Color.FromArgb(96, 100, 110);

        readonly PackBundle pack;
        readonly TextBox folder = new TextBox();
        readonly TrackBar memory = new TrackBar();
        readonly Label memoryLabel = new Label();
        readonly DropZone modsZone, tfZone;
        readonly TextBox opName = new TextBox(), players = new TextBox();
        readonly NumericUpDown port = new NumericUpDown();
        readonly CheckBox elytra = new CheckBox(), eula = new CheckBox();
        readonly Button install = new Button(), openFolder = new Button(), start = new Button();
        readonly ProgressBar bar = new ProgressBar();
        readonly Label status = new Label();
        readonly TextBox log = new TextBox();
        readonly Label existing = new Label();

        ModScan scan;
        string tfJar;
        CancellationTokenSource running;
        int totalRamGb;

        public MainForm(PackBundle pack)
        {
            this.pack = pack;
            SuspendLayout();
            AutoScaleDimensions = new SizeF(96f, 96f);
            AutoScaleMode = AutoScaleMode.Dpi;
            Text = "Create Galore Server Setup " + pack.PackVersion;
            Font = new Font("Segoe UI", 9f);
            BackColor = Color.White;
            ClientSize = new Size(660, 800);
            MinimumSize = new Size(560, 600);
            StartPosition = FormStartPosition.CenterScreen;
            try { Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath); } catch { }

            var header = new Panel { Dock = DockStyle.Top, Height = 74, BackColor = Dark };
            header.Controls.Add(new Label { Text = "Create Galore server", ForeColor = Color.White, Font = new Font("Segoe UI Semibold", 16f), AutoSize = true, Location = new Point(18, 10) });
            header.Controls.Add(new Label
            {
                Text = "Pack " + pack.PackVersion + "  ·  Minecraft " + pack.Minecraft + "  ·  NeoForge " + pack.NeoForge + "  ·  " + pack.Mods.Count + " server mods",
                ForeColor = Color.FromArgb(214, 190, 150), AutoSize = true, Location = new Point(20, 46),
            });

            var body = new TableLayoutPanel { Dock = DockStyle.Fill, ColumnCount = 1, Padding = new Padding(16, 8, 16, 12), AutoScroll = true };
            body.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));

            // 1. Folder
            body.Controls.Add(Heading("1  Where the server goes"));
            var folderRow = Row();
            folder.Width = 470;
            folder.Text = ServerInstaller.IsWindows ? @"C:\CreateGaloreServer" : Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "CreateGaloreServer");
            folder.TextChanged += (s, e) => ReadExisting();
            var browse = SmallButton("Browse…");
            browse.Click += (s, e) =>
            {
                using (var d = new FolderBrowserDialog { Description = "Pick or make an empty folder for the server", SelectedPath = folder.Text })
                    if (d.ShowDialog(this) == DialogResult.OK) folder.Text = d.SelectedPath;
            };
            folderRow.Controls.Add(folder);
            folderRow.Controls.Add(browse);
            body.Controls.Add(folderRow);
            existing.AutoSize = true;
            existing.ForeColor = Muted;
            existing.Margin = new Padding(3, 0, 3, 4);
            body.Controls.Add(existing);

            // 2. Memory
            body.Controls.Add(Heading("2  Memory"));
            totalRamGb = TotalRamGb();
            memory.Minimum = 4;
            memory.Maximum = Math.Max(12, Math.Min(32, totalRamGb > 0 ? totalRamGb - 4 : 16));
            memory.Value = 8;
            memory.TickFrequency = 2;
            memory.Width = 400;
            memory.ValueChanged += (s, e) => ShowMemory();
            var memRow = Row();
            memRow.Controls.Add(memory);
            memoryLabel.AutoSize = true;
            memoryLabel.Margin = new Padding(6, 12, 3, 3);
            memoryLabel.Font = new Font(Font, FontStyle.Bold);
            memRow.Controls.Add(memoryLabel);
            body.Controls.Add(memRow);
            body.Controls.Add(Note("8 GB is what the pack was tested with. If you play on the same PC, the game needs its own 8 to 10 GB on top."
                + (totalRamGb > 0 ? " This PC has " + totalRamGb + " GB." : "")));
            ShowMemory();

            // 3. Mods
            body.Controls.Add(Heading("3  Mods"));
            body.Controls.Add(Note("The " + pack.Mods.Count + " server mods download automatically from Modrinth when you press Install, at the exact versions the pack uses. Client-only mods (shaders, Distant Horizons, sound and map add-ons) are left out."));
            modsZone = new DropZone("Optional: drop your mods folder here (or click) to copy matching mods instead of downloading them", true);
            modsZone.Picked += path => ScanMods(path);
            body.Controls.Add(modsZone);

            // 4. Twilight Forest
            body.Controls.Add(Heading("4  Twilight Forest"));
            var tfNote = Note("Twilight Forest is only on CurseForge, so it can't download automatically. Drop " + pack.TfFile + " here, the same jar your game uses.");
            body.Controls.Add(tfNote);
            var cf = new LinkLabel { Text = "Get it on CurseForge", AutoSize = true, Margin = new Padding(3, 0, 3, 4) };
            cf.LinkClicked += (s, e) => Open("https://www.curseforge.com/minecraft/mc-mods/the-twilight-forest/files/all?version=1.21.1");
            body.Controls.Add(cf);
            tfZone = new DropZone("Drop the Twilight Forest jar here (or click)", false);
            tfZone.Picked += path => CheckTf(path);
            body.Controls.Add(tfZone);

            // 5. Players and settings
            body.Controls.Add(Heading("5  Players and settings"));
            var grid = new TableLayoutPanel { ColumnCount = 2, AutoSize = true, Margin = new Padding(0) };
            opName.Width = 200;
            players.Width = 300;
            port.Minimum = 1024; port.Maximum = 65535; port.Value = 25565; port.Width = 90;
            grid.Controls.Add(FieldLabel("Your Minecraft name"), 0, 0); grid.Controls.Add(opName, 1, 0);
            grid.Controls.Add(FieldLabel("Other players"), 0, 1); grid.Controls.Add(players, 1, 1);
            grid.Controls.Add(FieldLabel("Port"), 0, 2); grid.Controls.Add(port, 1, 2);
            body.Controls.Add(grid);
            body.Controls.Add(Note("Names are added to the whitelist (yours also becomes operator). Separate other players with commas. You can leave these empty and use whitelist add in the console later."));
            elytra.Text = "Allow Elytra (off by default so trains, airships and submarines stay worth building)";
            elytra.AutoSize = true;
            body.Controls.Add(elytra);
            eula.AutoSize = true;
            eula.Text = "I accept the Minecraft EULA (the server won't start without it)";
            var eulaRow = Row();
            eulaRow.Controls.Add(eula);
            var eulaLink = new LinkLabel { Text = "read it", AutoSize = true, Margin = new Padding(0, 6, 3, 3) };
            eulaLink.LinkClicked += (s, e) => Open("https://aka.ms/MinecraftEULA");
            eulaRow.Controls.Add(eulaLink);
            body.Controls.Add(eulaRow);

            // Install
            install.Text = "Install server";
            install.Font = new Font("Segoe UI Semibold", 11f);
            install.BackColor = Brass;
            install.ForeColor = Color.White;
            install.FlatStyle = FlatStyle.Flat;
            install.FlatAppearance.BorderSize = 0;
            install.Size = new Size(200, 40);
            install.Margin = new Padding(3, 14, 3, 6);
            install.Click += (s, e) => InstallClicked();
            body.Controls.Add(install);
            bar.Width = 610; bar.Height = 14; bar.Maximum = 1000;
            body.Controls.Add(bar);
            status.AutoSize = true; status.ForeColor = Muted; status.Text = "Ready.";
            body.Controls.Add(status);
            log.Multiline = true; log.ReadOnly = true; log.ScrollBars = ScrollBars.Vertical;
            log.Width = 610; log.Height = 150; log.Font = new Font("Consolas", 8.5f); log.BackColor = Color.FromArgb(246, 246, 248);
            body.Controls.Add(log);
            var done = Row();
            openFolder.Text = "Open server folder"; openFolder.AutoSize = true; openFolder.Enabled = false;
            openFolder.Click += (s, e) => Open(folder.Text);
            start.Text = "Start server"; start.AutoSize = true; start.Enabled = false;
            start.Click += (s, e) => StartServer();
            done.Controls.Add(openFolder);
            done.Controls.Add(start);
            body.Controls.Add(done);

            Controls.Add(body);
            Controls.Add(header);
            ResumeLayout(false);
            PerformLayout();
            ReadExisting();
        }

        // ---------- small builders ----------

        static Label Heading(string text)
        {
            return new Label { Text = text, AutoSize = true, Font = new Font("Segoe UI Semibold", 11f), ForeColor = Dark, Margin = new Padding(0, 12, 3, 4) };
        }

        static Label Note(string text)
        {
            return new Label { Text = text, AutoSize = true, MaximumSize = new Size(610, 0), ForeColor = Muted, Margin = new Padding(3, 0, 3, 6) };
        }

        static Label FieldLabel(string text)
        {
            return new Label { Text = text, AutoSize = true, Margin = new Padding(3, 7, 12, 3) };
        }

        static FlowLayoutPanel Row()
        {
            return new FlowLayoutPanel { AutoSize = true, WrapContents = false, Margin = new Padding(0), FlowDirection = FlowDirection.LeftToRight };
        }

        static Button SmallButton(string text)
        {
            return new Button { Text = text, AutoSize = true, Margin = new Padding(6, 1, 3, 3) };
        }

        [StructLayout(LayoutKind.Sequential)]
        struct MemoryStatusEx
        {
            public uint Length, MemoryLoad;
            public ulong TotalPhys, AvailPhys, TotalPageFile, AvailPageFile, TotalVirtual, AvailVirtual, AvailExtendedVirtual;
        }

        [DllImport("kernel32.dll", SetLastError = true)]
        static extern bool GlobalMemoryStatusEx(ref MemoryStatusEx status);

        static int TotalRamGb()
        {
            try
            {
                var m = new MemoryStatusEx { Length = (uint)Marshal.SizeOf(typeof(MemoryStatusEx)) };
                return GlobalMemoryStatusEx(ref m) ? (int)Math.Round(m.TotalPhys / 1073741824.0) : 0;
            }
            catch { return 0; }
        }

        static void Open(string target)
        {
            try { Process.Start(new ProcessStartInfo(target) { UseShellExecute = true }); } catch { }
        }

        // ---------- behaviour ----------

        /// <summary>Runs on the UI thread from a worker; does nothing once the window is closed.</summary>
        void Ui(Action a)
        {
            try { if (!IsDisposed && IsHandleCreated) BeginInvoke(a); } catch (InvalidOperationException) { }
        }

        void ShowMemory()
        {
            memoryLabel.Text = memory.Value + " GB" + (memory.Value == 8 ? " (tested)" : "");
        }

        void ReadExisting()
        {
            string dir = folder.Text.Trim();
            bool has = dir.Length > 0 && Directory.Exists(dir) && File.Exists(Path.Combine(dir, "server.properties"));
            existing.Text = has ? "There's already a server here. Install updates it and keeps the world and your settings." : "";
            if (!has) return;
            int mem = ServerInstaller.ReadMemory(dir);
            if (mem >= memory.Minimum && mem <= memory.Maximum) memory.Value = mem;
            int p = ServerInstaller.ReadPort(dir);
            if (p >= port.Minimum && p <= port.Maximum) port.Value = p;
            elytra.Checked = ServerInstaller.ElytraIsOn(dir);
            if (tfJar == null)
            {
                var tf = Directory.Exists(Path.Combine(dir, "mods")) ? Directory.GetFiles(Path.Combine(dir, "mods"), "twilightforest*.jar").FirstOrDefault() : null;
                if (tf != null) CheckTf(tf);
            }
        }

        async void ScanMods(string path)
        {
            if (File.Exists(path)) path = Path.GetDirectoryName(path);
            modsZone.SetResult(null, "Checking " + path + " …");
            try
            {
                var s = await Task.Run(() => ModScan.Scan(path, pack, (i, n) => Ui(() => modsZone.SetResult(null, "Checking jars… " + i + " of " + n))));
                scan = s;
                string msg = s.Found.Count + " of " + pack.Mods.Count + " server mods found, copied instead of downloaded";
                if (s.Missing(pack) > 0) msg += ". The other " + s.Missing(pack) + " download automatically";
                if (s.Skipped > 0) msg += ". " + s.Skipped + " other jars ignored (client-only or not in the pack)";
                modsZone.SetResult(s.Found.Count > 0, msg + ".");
                if (s.TfJar != null && tfJar == null) CheckTf(s.TfJar);
            }
            catch (Exception e)
            {
                scan = null;
                modsZone.SetResult(false, "Couldn't read that folder: " + e.Message);
            }
        }

        void CheckTf(string path)
        {
            if (Directory.Exists(path))
                path = Directory.GetFiles(path, "twilightforest*.jar").FirstOrDefault() ?? path;
            if (!File.Exists(path)) { tfZone.SetResult(false, "No Twilight Forest jar there."); return; }
            var r = TwilightCheck.Check(path, pack);
            tfJar = r.Ok ? path : null;
            tfZone.SetResult(r.Ok, r.Message);
        }

        async void InstallClicked()
        {
            if (running != null) { running.Cancel(); install.Enabled = false; status.Text = "Stopping…"; return; }
            string dir = folder.Text.Trim();
            if (dir.Length == 0 || !Path.IsPathRooted(dir)) { MessageBox.Show(this, "Pick a full folder path for the server, for example C:\\CreateGaloreServer.", Text); return; }
            if (tfJar == null && MessageBox.Show(this, "Twilight Forest isn't added yet. The server won't start without it.\n\nInstall everything else now and add the jar later?", Text, MessageBoxButtons.OKCancel, MessageBoxIcon.Warning) != DialogResult.OK) return;
            if (!eula.Checked && MessageBox.Show(this, "You haven't accepted the Minecraft EULA, so the server won't start until eula=true is in eula.txt.\n\nInstall anyway?", Text, MessageBoxButtons.OKCancel, MessageBoxIcon.Information) != DialogResult.OK) return;

            var options = new SetupOptions
            {
                ServerDir = dir, MemoryGb = memory.Value, Port = (int)port.Value, AcceptEula = eula.Checked, Elytra = elytra.Checked,
                OpName = opName.Text.Trim().Length > 0 ? opName.Text.Trim() : null,
                Players = players.Text.Split(new[] { ',', ';', ' ', '\n' }, StringSplitOptions.RemoveEmptyEntries).ToList(),
                TfJar = tfJar, LocalMods = scan,
            };
            running = new CancellationTokenSource();
            SetBusy(true);
            log.Clear();
            var installer = new ServerInstaller(pack, options,
                line => Ui(() => log.AppendText(line + Environment.NewLine)),
                (at, what) => Ui(() => { bar.Value = Math.Max(0, Math.Min(1000, (int)(at * 1000))); status.Text = what; }),
                running.Token);
            try
            {
                await Task.Run(() => installer.RunAsync());
                bool complete = installer.MissingMods.Count == 0 && tfJar != null && eula.Checked;
                status.ForeColor = complete ? Good : Brass;
                status.Text = complete ? "Server ready. Press Start server, then join localhost in Minecraft." : "Installed, with something left to do (see the log).";
                openFolder.Enabled = true;
                start.Enabled = complete;
            }
            catch (OperationCanceledException) { status.ForeColor = Muted; status.Text = "Stopped. Press Install again to pick up where it left off."; }
            catch (Exception e)
            {
                status.ForeColor = Bad;
                status.Text = "Install failed: " + e.Message;
                log.AppendText("ERROR: " + e + Environment.NewLine);
            }
            finally
            {
                running = null;
                SetBusy(false);
            }
        }

        void SetBusy(bool busy)
        {
            foreach (Control c in new Control[] { folder, memory, modsZone, tfZone, opName, players, port, elytra, eula })
                c.Enabled = !busy;
            install.Enabled = true;
            install.Text = busy ? "Stop" : "Install server";
            if (busy) { status.ForeColor = Muted; openFolder.Enabled = start.Enabled = false; }
        }

        void StartServer()
        {
            string dir = folder.Text.Trim();
            string script = Path.Combine(dir, ServerInstaller.IsWindows ? "start-server.bat" : "start-server.sh");
            try { Process.Start(new ProcessStartInfo(script) { UseShellExecute = true, WorkingDirectory = dir }); }
            catch (Exception e) { MessageBox.Show(this, "Couldn't start it: " + e.Message, Text); }
        }

        protected override void OnShown(EventArgs e)
        {
            base.OnShown(e);
            var area = Screen.FromControl(this).WorkingArea;
            if (Height > area.Height) { Top = area.Top; Height = area.Height; }
            folder.SelectionLength = 0;
            install.Focus();
        }

        protected override void OnFormClosing(FormClosingEventArgs e)
        {
            if (running != null && MessageBox.Show(this, "The install is still running. Stop it and close?", Text, MessageBoxButtons.YesNo) != DialogResult.Yes)
            {
                e.Cancel = true;
                return;
            }
            if (running != null) running.Cancel();
            base.OnFormClosing(e);
        }
    }

    /// <summary>A dashed box that takes a dropped file or folder, or a click to browse, and shows a ✔/✖ result.</summary>
    sealed class DropZone : Panel
    {
        readonly bool folders;
        readonly Label text = new Label(), result = new Label();
        bool hover;
        public event Action<string> Picked;

        public DropZone(string prompt, bool folders)
        {
            this.folders = folders;
            AllowDrop = true;
            Width = 610; Height = 74;
            Margin = new Padding(3, 2, 3, 6);
            Cursor = Cursors.Hand;
            BackColor = Color.FromArgb(250, 247, 242);
            text.Text = prompt; text.AutoSize = false; text.TextAlign = ContentAlignment.MiddleCenter;
            text.SetBounds(10, 8, Width - 20, 30);
            result.AutoSize = false; result.TextAlign = ContentAlignment.MiddleCenter; result.Font = new Font("Segoe UI Semibold", 9f);
            result.SetBounds(10, 38, Width - 20, 30);
            foreach (var l in new[] { text, result })
            {
                l.BackColor = Color.Transparent;
                l.Click += (s, e) => Browse();
                Controls.Add(l);
            }
            Click += (s, e) => Browse();
            DragEnter += (s, e) =>
            {
                e.Effect = e.Data.GetDataPresent(DataFormats.FileDrop) ? DragDropEffects.Copy : DragDropEffects.None;
                hover = true; Invalidate();
            };
            DragLeave += (s, e) => { hover = false; Invalidate(); };
            DragDrop += (s, e) =>
            {
                hover = false; Invalidate();
                var files = e.Data.GetData(DataFormats.FileDrop) as string[];
                if (files != null && files.Length > 0 && Picked != null) Picked(files[0]);
            };
        }

        public void SetResult(bool? ok, string message)
        {
            result.ForeColor = ok == true ? Color.FromArgb(32, 140, 72) : ok == false ? Color.FromArgb(196, 52, 52) : Color.FromArgb(96, 100, 110);
            result.Text = (ok == true ? "✔  " : ok == false ? "✖  " : "") + message;
        }

        void Browse()
        {
            if (!Enabled || Picked == null) return;
            if (folders)
            {
                using (var d = new FolderBrowserDialog { Description = "Pick a mods folder, for example your Modrinth or Prism instance's mods folder" })
                    if (d.ShowDialog(FindForm()) == DialogResult.OK) Picked(d.SelectedPath);
            }
            else
            {
                using (var d = new OpenFileDialog { Filter = "Mod jar (*.jar)|*.jar", Title = "Pick the Twilight Forest jar" })
                    if (d.ShowDialog(FindForm()) == DialogResult.OK) Picked(d.FileName);
            }
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            base.OnPaint(e);
            using (var pen = new Pen(hover ? Color.FromArgb(184, 124, 52) : Color.FromArgb(200, 190, 175), hover ? 2f : 1.5f) { DashStyle = DashStyle.Dash })
                e.Graphics.DrawRectangle(pen, 1, 1, Width - 3, Height - 3);
        }
    }
}

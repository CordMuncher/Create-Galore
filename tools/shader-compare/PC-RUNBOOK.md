# Shader compare on the PC: runbook

For the Claude Code session on the test PC (a local session opened in the test folder). Start only when the PC's owner
says so. Rules that come before anything here:

- Work only inside `C:\Users\coolm\Documents\Claude\MC-CREATE` (the test folder). Never open, read or change the
  owner's Prism instance, pack install, worlds, server files or anything else outside that folder. Nothing installed
  system-wide, no PATH or registry changes.
- No need to ask before each download, install or launch in this runbook once the owner has said to run it. Ask the
  owner before any tool or step that is not in it.
- The game logs in offline as `CGTester`; never use the owner's accounts.
- Results go to the shader pack branch only (`claude/project-thread-mturba`); nothing is merged or released.
- It only photographs the packs. Nothing is taken from them.

PowerShell, in the test folder:

```powershell
$T = "C:\Users\coolm\Documents\Claude\MC-CREATE"
$R = "$T\repo-shaders"     # this branch
$I = "$T\compare"          # its own test instance, apart from the DLAA rounds' $T\instance
$java = "$T\tools\jdk21\bin\java.exe"
$env:UV_CACHE_DIR = "$T\tools\uv-cache"; $env:UV_TOOL_DIR = "$T\tools\uv-tools"; $env:UV_PYTHON_INSTALL_DIR = "$T\tools\uv-python"
```

## 1. Tools and the branch

Java 21 and uv are in `$T\tools` from the DLAA rounds. If `$java` or `$T\tools\uv\uv.exe` is missing, make them as
section 1 of `tools\dlss-testkit\PC-RUNBOOK.md` does (that file is in this branch too).

```powershell
if (Test-Path $R) { git -C $R pull } else {
  git clone --branch claude/project-thread-mturba --single-branch https://github.com/emresahin0906-code/Create-Galore.git $R }
```

## 2. Game and pack

The newest released pack (0.6.0 or later: our shader pack needs the Bridge from 0.5.12 on), client side, into its own
instance:

```powershell
New-Item -ItemType Directory $I -Force | Out-Null
& $T\tools\uv\uv.exe tool run --python 3.13 portablemc@5.0.6 --main-dir $T\mc start --dry --jvm $java --mc-dir $I neoforge::21.1.262
Invoke-WebRequest "https://github.com/packwiz/packwiz-installer-bootstrap/releases/latest/download/packwiz-installer-bootstrap.jar" -OutFile $I\packwiz-installer-bootstrap.jar
Push-Location $I
& $java -jar packwiz-installer-bootstrap.jar -g -s client "https://raw.githubusercontent.com/emresahin0906-code/Create-Galore/main-6fi2ef/pack/pack.toml"
Pop-Location
```

If the installer stops on a CurseForge-only file, take it from the repo's `pack\` folder (same as a normal install).
DLSS for our pack: copy NVIDIA's DLL from the DLAA instance, or fetch the same one the DLAA runbook does.

```powershell
New-Item -ItemType Directory -Force $I\create-galore-dlss | Out-Null
if (Test-Path $T\instance\create-galore-dlss\nvngx_dlss.dll) { Copy-Item $T\instance\create-galore-dlss\nvngx_dlss.dll $I\create-galore-dlss\ }
else { Invoke-WebRequest "https://raw.githubusercontent.com/NVIDIA/DLSS/9981c6350dc58c0ce21f024fd65e31e851c31689/lib/Windows_x86_64/rel/nvngx_dlss.dll" -OutFile $I\create-galore-dlss\nvngx_dlss.dll }
```

## 3. The kit, the packs, SPBR, the world, settings

```powershell
Copy-Item $R\tools\shader-compare\dist\create-galore-shader-compare-1.0.0.jar $I\mods\ -Force
New-Item -ItemType Directory -Force $I\config, $I\shaderpacks, $I\resourcepacks, $I\saves | Out-Null
Copy-Item $R\tools\shader-compare\create_galore_shader_compare.json $I\config\ -Force
$cfg = Get-Content $R\tools\shader-compare\create_galore_shader_compare.json -Raw | ConvertFrom-Json

# The other packs, each checked against its SHA-512 from Modrinth.
function Get-Checked($url, $dest, $sha512) {
  if (-not (Test-Path $dest) -or (Get-FileHash $dest -Algorithm SHA512).Hash -ne $sha512) { Invoke-WebRequest $url -OutFile $dest }
  if ((Get-FileHash $dest -Algorithm SHA512).Hash -ne $sha512) { throw "$dest does not match its hash" }
}
foreach ($p in $cfg.packs) { if ($p.url) { Get-Checked $p.url (Join-Path $I\shaderpacks $p.file) $p.sha512 } }
Get-Checked $cfg.resourcePack.url (Join-Path $I\resourcepacks $cfg.resourcePack.file) $cfg.resourcePack.sha512

# Ours, from this branch, as a folder (Iris doesn't follow links).
Remove-Item -Recurse -Force $I\shaderpacks\Create-Galore-Shaders -ErrorAction SilentlyContinue
New-Item -ItemType Directory $I\shaderpacks\Create-Galore-Shaders | Out-Null
Copy-Item -Recurse $R\shaderpack\shaders, $R\shaderpack\LICENSE, $R\shaderpack\README.md, $R\shaderpack\THIRD-PARTY-NOTICES.md $I\shaderpacks\Create-Galore-Shaders\
```

The world: copy the DLAA instance's, which has Distant Horizons' far terrain built already; else unzip the repo's.

```powershell
if (-not (Test-Path $I\saves\dlaa-test)) {
  if (Test-Path $T\instance\saves\dlaa-test) { Copy-Item -Recurse $T\instance\saves\dlaa-test $I\saves\ }
  else { Expand-Archive $R\tools\dlss-testkit\world\dlaa-test.zip $I\saves }
}
```

Before the first launch (create a file if it isn't there; the game adds the keys it misses), keeping a copy of each
file the pack installed as `<file>.before`:

- `$I\options.txt`: `pauseOnLostFocus:false`, `fullscreen:false`, `tutorialStep:none`, `renderDistance:12`, and
  SPBR on top of the resource packs: add `"file/SPBR-16_2.zip"` as the last entry of the `resourcePacks:[...]` list
  (the last one wins), or add `resourcePacks:["vanilla","mod_resources","file/SPBR-16_2.zip"]` if the line is missing.
- `$I\config\create_galore_bridge-display.properties`: `dlaa=dlss`, `perfectPresentation=false`, `frameGen=off`
  (our pack anti-aliases with DLSS at full resolution, the others with their own; no frame generation).
- `$I\config\iris.properties`: `enableShaders=true`, `shaderPack=Create-Galore-Shaders`.
- `$I\config\fml.toml`: `earlyWindowControl = false` (the window keeps the full `--resolution`).
- `$I\config\DistantHorizons.toml`: `lodChunkRenderDistanceRadius = 64`, and `numberOfThreads = 32` under
  `[common.multiThreading]`, as in the DLAA rounds (the copied world's far terrain was built at 64).

## 4. First launch (checks only)

```powershell
& $T\tools\uv\uv.exe tool run --python 3.13 portablemc@5.0.6 --main-dir $T\mc start --jvm $java --mc-dir $I `
  -u CGTester --resolution 1920x1080 --jvm-arg=-Xmx12G --jvm-arg=-Xms4G neoforge::21.1.262
```

Open the world `dlaa-test` by hand and look once: the resource pack screen lists SPBR at the top, blocks show SPBR's
look with our pack, the log (`$I\logs\latest.log`) has `DLAA: DLSS running at 1920x1080` and no
`Failed to create shader rendering pipeline`. Close the game.

If the world came from the repo's zip (no far terrain yet): copy `$R\tools\dlss-testkit\dist\create-galore-dlss-testkit-1.0.0.jar`
into `$I\mods\`, launch once more with `--jvm-arg=-Dcreate_galore_dlss_testkit.autorun=dlaa-test`
`--jvm-arg=-Dcreate_galore_dlss_testkit.hold=1200` (it stands at the lake shore for 20 minutes while the far terrain
builds, and closes), then delete that jar from `$I\mods\` again.

## Shader compare 1

### Run

The launch command from step 4 with `--jvm-arg=-Dcreate_galore_shader_compare.autorun=dlaa-test` added. The game
opens the world, photographs every scene with every pack (pass 1, each pack's defaults, then pass 2, each pack's
highest preset; 13 packs, 22 scenes), puts Iris back as it was and closes. It takes about two and a half hours; the
chat (hidden in the shots) and the log say `Shader compare: now <pack>-<pass>` at each pack. Keep the window visible;
it doesn't need focus. Don't use the PC for games meanwhile (the frame rate doesn't matter, but another full-screen
program can cover the window).

If it stops part way, run again with `--jvm-arg=-Dcreate_galore_shader_compare.packs=a+b` (the pack names in the
JSON) for the packs left; each run gets its own folder.

### Results

The run folder is `$I\screenshots\shader-compare\<run>\`. Contact sheets, one per scene (a few hundred KB each):

```powershell
& $T\tools\uv\uv.exe run --python 3.13 --with pillow $R\tools\shader-compare\sheet.py $R\tools\shader-compare\results\compare-1\sheets <run folder> [<more run folders>]
```

Write `$R\tools\shader-compare\results\compare-1\REPORT.md`:

- The run's `outcome` from `manifest.json`, how long it took, the GPU (`renderer`) and Iris version.
- Every pack and pass with `"loaded": false` and its `error`, and for each the Iris lines from `latest.log` that say
  why (compile errors: the first few lines of each).
- The spot the Nether scene found (`found` in the manifest).
- Look at every sheet: any scene that is broken for every pack (the camera inside a block, black, or nothing of what
  its `about` in the JSON says) and any pack that is broken in a scene (a black or white frame, the sky only, no
  shaders). Don't fix scenes or packs here; say what you saw.

Commit the report and the sheets on `claude/project-thread-mturba`, push, and give the owner the commit. Never commit
the full screenshots or logs. The full screenshots are looked at with `$R\tools\dlss-testkit\compare.html` (pick the
run folder; each pack and pass is one of its "modes").

## 20261025-133250-F1: NVIDIA GeForce RTX 4080 SUPER/PCIe/SSE2, Photon v1.3a + DLAA, 2560x1440 (run finished)

F.csv: 162141 frames, 21980 inside the run (columns frame=MsBetweenAppStart, cpu=MsCPUBusy, gpu=MsGPUBusy, start=CPUStartQPCTimeInMs)

## Frame times over the camera paths

| viewpoint | mode | frames | frame ms (median / p90) | repeats | CPU busy ms | GPU busy ms | GPU-bound |
| --- | --- | --- | --- | --- | --- | --- | --- |
| shore | photon-taa | 580 | 4.70 / 7.08 | - | 4.70 | 4.40 | 55% |
| canopy | photon-taa | 580 | 5.21 / 7.04 | - | 5.21 | 5.21 | 77% |
| shore | dlaa-default | 580 | 5.56 / 6.67 | - | 5.56 | 5.53 | 81% |
| canopy | dlaa-default | 580 | 6.31 / 6.44 | - | 6.31 | 6.31 | 93% |
| shore | fsr3 | 580 | 5.53 / 5.87 | - | 5.53 | 5.53 | 93% |
| canopy | fsr3 | 580 | 6.27 / 6.40 | - | 6.27 | 6.28 | 96% |
| shore | perf-taau | 580 | 3.55 / 5.19 | - | 3.55 | 3.52 | 68% |
| canopy | perf-taau | 580 | 3.95 / 4.81 | - | 3.95 | 3.96 | 83% |
| shore | perf-dlss-K | 580 | 4.44 / 5.13 | - | 4.44 | 4.41 | 88% |
| canopy | perf-dlss-K | 580 | 4.83 / 5.23 | - | 4.83 | 4.82 | 89% |
| shore | perf-dlss-M | 580 | 4.64 / 5.07 | - | 4.64 | 4.62 | 91% |
| canopy | perf-dlss-M | 580 | 5.03 / 5.14 | - | 5.03 | 5.02 | 95% |
| shore | perf-dlss-L | 580 | 4.85 / 4.97 | - | 4.85 | 4.84 | 94% |
| canopy | perf-dlss-L | 580 | 5.25 / 5.34 | - | 5.25 | 5.24 | 97% |
| shore | perf-fsr3 | 580 | 3.93 / 4.77 | - | 3.93 | 3.94 | 85% |
| canopy | perf-fsr3 | 580 | 4.34 / 4.47 | - | 4.34 | 4.35 | 92% |
| shore | ultra-taau | 580 | 3.22 / 4.25 | - | 3.22 | 3.27 | 77% |
| canopy | ultra-taau | 580 | 3.58 / 4.21 | - | 3.58 | 3.62 | 85% |
| shore | ultra-dlss-K | 580 | 4.13 / 4.67 | - | 4.13 | 4.13 | 88% |
| canopy | ultra-dlss-K | 580 | 4.46 / 4.87 | - | 4.46 | 4.45 | 90% |
| shore | ultra-dlss-M | 580 | 4.10 / 4.84 | - | 4.10 | 4.08 | 85% |
| canopy | ultra-dlss-M | 580 | 4.43 / 4.78 | - | 4.43 | 4.41 | 90% |
| shore | ultra-dlss-L | 580 | 4.15 / 4.65 | - | 4.15 | 4.15 | 89% |
| canopy | ultra-dlss-L | 580 | 4.53 / 6.00 | - | 4.53 | 4.51 | 80% |
| shore | ultra-fsr3 | 580 | 3.55 / 3.94 | - | 3.55 | 3.56 | 88% |
| canopy | ultra-fsr3 | 580 | 3.90 / 4.24 | - | 3.90 | 3.91 | 90% |


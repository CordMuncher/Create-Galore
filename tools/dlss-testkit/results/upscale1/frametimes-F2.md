## 20261025-133506-F2: NVIDIA GeForce RTX 4080 SUPER/PCIe/SSE2, Photon v1.3a + DLAA, 2560x1440 (run finished)

F.csv: 162141 frames, 18700 inside the run (columns frame=MsBetweenAppStart, cpu=MsCPUBusy, gpu=MsGPUBusy, start=CPUStartQPCTimeInMs)

## Frame times over the camera paths

| viewpoint | mode | frames | frame ms (median / p90) | repeats | CPU busy ms | GPU busy ms | GPU-bound |
| --- | --- | --- | --- | --- | --- | --- | --- |
| shore | photon-taa | 580 | 4.50 / 4.70 | - | 4.50 | 4.50 | 94% |
| canopy | photon-taa | 580 | 5.21 / 5.32 | - | 5.21 | 5.22 | 94% |
| shore | dlaa-default | 580 | 5.55 / 5.71 | - | 5.55 | 5.55 | 98% |
| canopy | dlaa-default | 580 | 6.32 / 6.40 | - | 6.32 | 6.32 | 98% |
| shore | fsr3 | 580 | 5.55 / 5.73 | - | 5.55 | 5.56 | 99% |
| canopy | fsr3 | 580 | 6.30 / 6.39 | - | 6.30 | 6.31 | 99% |
| shore | q-taau | 580 | 3.95 / 4.28 | - | 3.95 | 3.94 | 91% |
| canopy | q-taau | 580 | 4.40 / 4.51 | - | 4.40 | 4.41 | 95% |
| shore | q-dlss-K | 580 | 4.81 / 4.92 | - | 4.81 | 4.80 | 97% |
| canopy | q-dlss-K | 580 | 5.28 / 5.37 | - | 5.28 | 5.28 | 96% |
| shore | q-fsr3 | 580 | 4.46 / 4.62 | - | 4.46 | 4.45 | 95% |
| canopy | q-fsr3 | 580 | 4.90 / 5.00 | - | 4.90 | 4.91 | 98% |
| shore | bal-taau | 580 | 3.73 / 4.20 | - | 3.73 | 3.73 | 88% |
| canopy | bal-taau | 580 | 4.18 / 4.28 | - | 4.18 | 4.18 | 94% |
| shore | bal-dlss-K | 580 | 4.59 / 4.73 | - | 4.59 | 4.58 | 94% |
| canopy | bal-dlss-K | 580 | 5.04 / 5.13 | - | 5.04 | 5.03 | 96% |
| shore | bal-dlss-M | 580 | 5.00 / 5.11 | - | 5.00 | 4.99 | 98% |
| canopy | bal-dlss-M | 580 | 5.45 / 5.52 | - | 5.45 | 5.44 | 99% |
| shore | bal-dlss-L | 580 | 5.30 / 5.38 | - | 5.30 | 5.28 | 98% |
| canopy | bal-dlss-L | 580 | 5.73 / 5.81 | - | 5.73 | 5.72 | 98% |
| shore | bal-fsr3 | 580 | 4.18 / 4.48 | - | 4.18 | 4.18 | 92% |
| canopy | bal-fsr3 | 580 | 4.61 / 4.70 | - | 4.61 | 4.61 | 96% |


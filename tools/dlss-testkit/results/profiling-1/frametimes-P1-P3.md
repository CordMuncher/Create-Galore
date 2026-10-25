## 20261025-094526: NVIDIA GeForce RTX 4080 SUPER/PCIe/SSE2, Photon v1.3a + DLAA, 2560x1440 (run finished)

P1.ft.csv: 14857 frames, 12860 inside the run (columns frame=MsBetweenAppStart, cpu=MsCPUBusy, gpu=MsGPUBusy, start=CPUStartQPCTime)

## 20261025-095242: NVIDIA GeForce RTX 4080 SUPER/PCIe/SSE2, Photon v1.3a + DLAA, 2560x1440 (run finished)

P2.ft.csv: 15580 frames, 12860 inside the run (columns frame=MsBetweenAppStart, cpu=MsCPUBusy, gpu=MsGPUBusy, start=CPUStartQPCTime)

## 20261025-095712: NVIDIA GeForce RTX 4080 SUPER/PCIe/SSE2, Photon v1.3a + DLAA, 2560x1440 (run finished)

P3.ft.csv: 15169 frames, 12860 inside the run (columns frame=MsBetweenAppStart, cpu=MsCPUBusy, gpu=MsGPUBusy, start=CPUStartQPCTime)

## Frame times over the camera paths

| viewpoint | mode | frames | frame ms (median / p90) | repeats | CPU busy ms | GPU busy ms | GPU-bound |
| --- | --- | --- | --- | --- | --- | --- | --- |
| shore | photon-taa | 1740 | 5.43 / 7.91 | 5.01 to 5.63 | 5.43 | 4.76 | 45% |
| glass | photon-taa | 1740 | 4.35 / 6.57 | 4.31 to 4.38 | 4.35 | 4.18 | 60% |
| canopy | photon-taa | 1740 | 5.87 / 8.82 | 5.51 to 6.14 | 5.87 | 5.60 | 62% |
| moving | photon-taa | 1740 | 6.88 / 14.22 | 6.57 to 7.19 | 6.88 | 5.99 | 45% |
| shore | dlaa-default | 1740 | 6.20 / 7.54 | 5.89 to 6.48 | 6.20 | 6.20 | 85% |
| glass | dlaa-default | 1740 | 5.60 / 7.02 | 5.35 to 5.75 | 5.60 | 5.60 | 83% |
| canopy | dlaa-default | 1740 | 7.31 / 7.97 | 6.94 to 7.68 | 7.31 | 7.24 | 90% |
| moving | dlaa-default | 1740 | 6.79 / 11.99 | 6.48 to 7.07 | 6.79 | 7.34 | 80% |
| shore | dlaa-camera | 1740 | 6.19 / 6.94 | 5.88 to 6.56 | 6.19 | 6.17 | 91% |
| glass | dlaa-camera | 1740 | 5.60 / 8.06 | 5.36 to 5.77 | 5.60 | 5.58 | 82% |
| canopy | dlaa-camera | 1740 | 7.31 / 7.85 | 6.93 to 7.64 | 7.31 | 7.20 | 93% |
| moving | dlaa-camera | 1740 | 6.79 / 11.03 | 6.60 to 7.24 | 6.79 | 7.34 | 85% |
| shore | fsr3 | 1740 | 6.17 / 6.80 | 5.88 to 6.57 | 6.17 | 6.16 | 98% |
| glass | fsr3 | 1740 | 5.55 / 6.20 | 5.35 to 5.72 | 5.55 | 5.54 | 93% |
| canopy | fsr3 | 1740 | 7.20 / 7.79 | 6.90 to 7.63 | 7.20 | 7.19 | 97% |
| moving | fsr3 | 1740 | 6.95 / 10.57 | 6.70 to 7.46 | 6.95 | 7.33 | 84% |


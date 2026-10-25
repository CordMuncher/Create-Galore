## 20261025-101009: NVIDIA GeForce RTX 4080 SUPER/PCIe/SSE2, Photon v1.3a + DLAA, 2560x1440 (run finished)

P5.ft.csv: 11727 frames, 5580 inside the run (columns frame=MsBetweenAppStart, cpu=MsCPUBusy, gpu=MsGPUBusy, start=CPUStartQPCTime)

## Frame times over the camera paths

| viewpoint | mode | frames | frame ms (median / p90) | repeats | CPU busy ms | GPU busy ms | GPU-bound |
| --- | --- | --- | --- | --- | --- | --- | --- |
| shore | photon-taa | 580 | 4.99 / 6.99 | - | 4.95 | 4.62 | 53% |
| canopy | photon-taa | 580 | 5.54 / 8.00 | - | 5.48 | 5.35 | 56% |
| shore | dlaa-default | 580 | 5.65 / 6.74 | - | 5.61 | 5.64 | 80% |
| canopy | dlaa-default | 580 | 6.31 / 7.30 | - | 6.27 | 6.37 | 84% |
| shore | fsr3 | 580 | 5.60 / 6.78 | - | 5.55 | 5.39 | 72% |
| canopy | fsr3 | 580 | 6.31 / 7.33 | - | 6.27 | 6.36 | 78% |


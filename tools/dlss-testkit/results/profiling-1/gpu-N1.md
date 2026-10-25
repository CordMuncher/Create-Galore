Session start on the performance counter: 5112760.2 ms. Render thread 281911922558352.

| mode | viewpoint | frames | frame period ms | blocked in SwapBuffers ms | glClientWaitSync+glFinish ms | GPU busy ms (GL work, union) | GPC MHz | GR active % | Tensor active % |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| photon-taa | canopy | 119 | 11.20 | 3.46 |0.00 | 5.38 | 2718 | 48 | 0.0 |
| photon-taa | moving | 119 | 19.07 | 3.92 |0.00 | 8.84 | 1967 | 39 | 0.0 |
| dlaa-default | canopy | 119 | 12.91 | 3.55 |0.00 | 5.75 | 2721 | 51 | 1.5 |
| dlaa-default | moving | 119 | 17.20 | 0.75 |0.00 | 7.47 | 2722 | 42 | 1.1 |
| fsr3 | canopy | 119 | 12.23 | 4.21 |0.00 | 6.55 | 2723 | 57 | 0.0 |
| fsr3 | moving | 119 | 13.21 | 4.31 |0.00 | 6.71 | 2722 | 51 | 0.0 |

### GPU ms per frame by part (median / mean over the frames)

**photon-taa, canopy** (119 frames, each from one SwapBuffers to the next; GL GPU work summed 5.38 ms)

| part | GPU ms median | GPU ms mean |
| --- | --- | --- |
| rest of the frame | 5.380 | 5.513 |

GL calls that held the render thread for more than 0.2 ms (mean ms per frame, number of such calls): SwapBuffers 2.96 (118), glClear 0.41 (13), glBindFramebuffer 0.12 (5), glBlitFramebuffer 0.06 (2), glDrawTextureNV 0.06 (2), glDispatchCompute 0.00 (1), glGenerateTextureMipmap 0.00 (1)

Rest of the frame by the GL call its GPU rows start at (mean ms per frame): glDrawElements 1.99, glMultiDrawElementsBaseVertex 1.44, glDispatchCompute 0.73, glBindFramebuffer 0.47, glClear 0.26, glCopyTexSubImage2D 0.22, glGenerateTextureMipmap 0.18, glCopyImageSubData 0.11, glMultiDrawElementsIndirect 0.04, glBlitFramebuffer 0.03

CPU side of the groups (median ms: whole group / its longest single call; count):
other group 0.008 / 0.000 (119)

**photon-taa, moving** (119 frames, each from one SwapBuffers to the next; GL GPU work summed 8.84 ms)

| part | GPU ms median | GPU ms mean |
| --- | --- | --- |
| rest of the frame | 8.839 | 8.799 |

GL calls that held the render thread for more than 0.2 ms (mean ms per frame, number of such calls): SwapBuffers 3.45 (119), glClear 0.39 (11), glBindFramebuffer 0.37 (15), glBlitFramebuffer 0.05 (2), glDrawTextureNV 0.04 (1), glDispatchCompute 0.04 (19), glMultiDrawElementsBaseVertex 0.00 (1)

Rest of the frame by the GL call its GPU rows start at (mean ms per frame): glDrawElements 2.47, glMultiDrawElementsBaseVertex 2.15, glDispatchCompute 1.76, glBindFramebuffer 0.82, glClearTexImage 0.37, glClear 0.29, glCopyTexSubImage2D 0.26, glGenerateTextureMipmap 0.22, glCopyBufferSubData 0.14, glCopyImageSubData 0.14, glMultiDrawElementsIndirect 0.12, glBlitFramebuffer 0.03, glDrawTextureNV 0.02

CPU side of the groups (median ms: whole group / its longest single call; count):
other group 0.010 / 0.000 (119)

**dlaa-default, canopy** (119 frames, each from one SwapBuffers to the next; GL GPU work summed 5.75 ms)

| part | GPU ms median | GPU ms mean |
| --- | --- | --- |
| rest of the frame | 5.403 | 6.004 |
| copy inputs: last copy's row, runs on to the write back (holds the DirectX gap) | 0.000 | 0.160 |
| copy inputs | 0.093 | 0.093 |
| write back | 0.084 | 0.084 |
| (rest of the frame: before the Bridge) | 4.907 | |
| (rest of the frame: after the Bridge) | 0.497 | |
| DLSS on DirectX + handover: the GL queue's gap from the last input copy to the write back | 1.057 (p10 1.033, p90 1.123) | |

During the DirectX gap: GR active 94%, Tensor active 18.3% (1281 samples).
Write back's draw blocked the CPU for more than 1 ms in 18 of 119 frames (median 3.94 ms when it did). GPU idle after the write back until the next GL work: median 0.001 ms (0.748 ms in frames with the block, 0.001 ms without).

GL calls that held the render thread for more than 0.2 ms (mean ms per frame, number of such calls): SwapBuffers 2.52 (119), glDrawArrays 0.61 (18), glBindFramebuffer 0.40 (15), glClear 0.31 (11), glDrawElements 0.10 (3), glCopyImageSubData 0.08 (3), glDrawTextureNV 0.03 (1), glMultiDrawElementsBaseVertex 0.00 (1)

Rest of the frame by the GL call its GPU rows start at (mean ms per frame): glDrawElements 1.93, glMultiDrawElementsBaseVertex 1.48, glDispatchCompute 0.95, glBindFramebuffer 0.48, glClearTexImage 0.29, glClear 0.27, glCopyTexSubImage2D 0.21, glGenerateTextureMipmap 0.17, glCopyImageSubData 0.11, glMultiDrawElementsIndirect 0.07, glBlitFramebuffer 0.03

CPU side of the groups (median ms: whole group / its longest single call; count):
copy inputs 0.017 / 0.003 (119), evaluate (DirectX) 0.438 / 0.000 (119), write back 0.034 / 0.010 (119), other group 0.005 / 0.000 (119)

**dlaa-default, moving** (119 frames, each from one SwapBuffers to the next; GL GPU work summed 7.47 ms)

| part | GPU ms median | GPU ms mean |
| --- | --- | --- |
| rest of the frame | 7.290 | 6.756 |
| copy inputs: last copy's row, runs on to the write back (holds the DirectX gap) | 0.000 | 0.225 |
| copy inputs | 0.084 | 0.084 |
| write back | 0.078 | 0.078 |
| (rest of the frame: before the Bridge) | 6.799 | |
| (rest of the frame: after the Bridge) | 0.488 | |
| DLSS on DirectX + handover: the GL queue's gap from the last input copy to the write back | 1.067 (p10 1.040, p90 1.142) | |

During the DirectX gap: GR active 93%, Tensor active 18.0% (1302 samples).
Write back's draw blocked the CPU for more than 1 ms in 25 of 119 frames (median 3.93 ms when it did). GPU idle after the write back until the next GL work: median 0.001 ms (0.786 ms in frames with the block, 0.001 ms without).

GL calls that held the render thread for more than 0.2 ms (mean ms per frame, number of such calls): SwapBuffers 1.71 (119), glDrawArrays 0.80 (25), glBindFramebuffer 0.64 (26), glClear 0.43 (14), glDrawElements 0.19 (9), glCopyTexSubImage2D 0.09 (3), glDrawTextureNV 0.03 (1), glCopyImageSubData 0.03 (1), glDispatchCompute 0.00 (2)

Rest of the frame by the GL call its GPU rows start at (mean ms per frame): glDrawElements 1.87, glMultiDrawElementsBaseVertex 1.65, glDispatchCompute 1.37, glBindFramebuffer 0.55, glClearTexImage 0.29, glClear 0.24, glCopyTexSubImage2D 0.20, glCopyBufferSubData 0.17, glGenerateTextureMipmap 0.17, glCopyImageSubData 0.11, glMultiDrawElementsIndirect 0.09, glBlitFramebuffer 0.03

CPU side of the groups (median ms: whole group / its longest single call; count):
copy inputs 0.020 / 0.004 (119), evaluate (DirectX) 0.518 / 0.000 (119), write back 0.038 / 0.011 (119), other group 0.007 / 0.000 (119)

**fsr3, canopy** (119 frames, each from one SwapBuffers to the next; GL GPU work summed 6.55 ms)

| part | GPU ms median | GPU ms mean |
| --- | --- | --- |
| rest of the frame | 5.355 | 5.609 |
| FSR accumulate | 0.307 | 0.307 |
| FSR shading_change_pyramid | 0.274 | 0.274 |
| FSR clears + prepare_inputs | 0.166 | 0.164 |
| FSR prepare_reactivity | 0.113 | 0.113 |
| copy inputs | 0.111 | 0.112 |
| FSR luma_instability | 0.102 | 0.102 |
| write back | 0.083 | 0.083 |
| FSR luma_pyramid | 0.028 | 0.028 |
| FSR shading_change | 0.012 | 0.012 |
| FSR prepare_inputs | 0.000 | 0.002 |
| FSR (between passes) | 0.000 | 0.002 |
| (rest of the frame: before the Bridge) | 4.861 | |
| (rest of the frame: after the Bridge) | 0.493 | |
| DLSS on DirectX + handover: the GL queue's gap from the last input copy to the write back | 1.020 (p10 1.013, p90 1.041) | |

During the DirectX gap: GR active 96%, Tensor active 0.0% (1274 samples).
Write back's draw blocked the CPU for more than 1 ms in 2 of 119 frames (median 4.61 ms when it did). GPU idle after the write back until the next GL work: median 0.001 ms (0.594 ms in frames with the block, 0.001 ms without).

GL calls that held the render thread for more than 0.2 ms (mean ms per frame, number of such calls): SwapBuffers 3.22 (119), glBindFramebuffer 0.58 (18), glCopyTexSubImage2D 0.23 (6), glDispatchCompute 0.19 (7), glClear 0.12 (3), glDrawElements 0.12 (4), glDrawArrays 0.08 (3), glCopyImageSubData 0.05 (2), glClearTexImage 0.05 (2), glBlitFramebuffer 0.04 (1), glDrawTextureNV 0.04 (1)

Rest of the frame by the GL call its GPU rows start at (mean ms per frame): glDrawElements 1.94, glMultiDrawElementsBaseVertex 1.48, glDispatchCompute 0.57, glBindFramebuffer 0.48, glClearTexImage 0.29, glClear 0.25, glCopyTexSubImage2D 0.21, glGenerateTextureMipmap 0.17, glCopyImageSubData 0.11, glMultiDrawElementsIndirect 0.07, glBlitFramebuffer 0.03

CPU side of the groups (median ms: whole group / its longest single call; count):
copy inputs 0.017 / 0.003 (119), evaluate (FSR) 0.093 / 0.005 (119), FSR prepare_inputs 0.011 / 0.005 (119), FSR luma_pyramid 0.007 / 0.004 (119), FSR shading_change_pyramid 0.006 / 0.003 (119), FSR shading_change 0.005 / 0.002 (119), FSR prepare_reactivity 0.008 / 0.003 (119), FSR luma_instability 0.006 / 0.003 (119), FSR accumulate 0.007 / 0.003 (119), write back 0.018 / 0.005 (119), other group 0.005 / 0.000 (119)

**fsr3, moving** (119 frames, each from one SwapBuffers to the next; GL GPU work summed 6.71 ms)

| part | GPU ms median | GPU ms mean |
| --- | --- | --- |
| rest of the frame | 5.527 | 5.933 |
| FSR accumulate | 0.303 | 0.303 |
| FSR shading_change_pyramid | 0.280 | 0.280 |
| FSR clears + prepare_inputs | 0.167 | 0.169 |
| FSR prepare_reactivity | 0.114 | 0.114 |
| copy inputs | 0.100 | 0.100 |
| FSR luma_instability | 0.092 | 0.093 |
| write back | 0.077 | 0.076 |
| FSR luma_pyramid | 0.028 | 0.028 |
| FSR shading_change | 0.012 | 0.012 |
| FSR (between passes) | 0.000 | 0.000 |
| (rest of the frame: before the Bridge) | 5.045 | |
| (rest of the frame: after the Bridge) | 0.485 | |
| DLSS on DirectX + handover: the GL queue's gap from the last input copy to the write back | 1.016 (p10 1.011, p90 1.026) | |

During the DirectX gap: GR active 97%, Tensor active 0.0% (1240 samples).
Write back's draw blocked the CPU for more than 1 ms in 0 of 119 frames (median 0.00 ms when it did). GPU idle after the write back until the next GL work: median 0.001 ms (0.000 ms in frames with the block, 0.001 ms without).

GL calls that held the render thread for more than 0.2 ms (mean ms per frame, number of such calls): SwapBuffers 3.48 (119), glBindFramebuffer 0.45 (13), glDrawElements 0.28 (7), glClear 0.22 (5), glCopyTexSubImage2D 0.12 (3), glDispatchCompute 0.11 (4), glDrawTextureNV 0.09 (2), glClearTexImage 0.03 (1), glCopyImageSubData 0.03 (1), glDrawArrays 0.00 (1)

Rest of the frame by the GL call its GPU rows start at (mean ms per frame): glDrawElements 1.87, glMultiDrawElementsBaseVertex 1.65, glBindFramebuffer 0.54, glDispatchCompute 0.52, glClearTexImage 0.29, glClear 0.24, glCopyTexSubImage2D 0.20, glCopyBufferSubData 0.20, glGenerateTextureMipmap 0.17, glCopyImageSubData 0.11, glMultiDrawElementsIndirect 0.09, glBlitFramebuffer 0.03

CPU side of the groups (median ms: whole group / its longest single call; count):
copy inputs 0.018 / 0.003 (119), evaluate (FSR) 0.096 / 0.005 (119), FSR prepare_inputs 0.012 / 0.005 (119), FSR luma_pyramid 0.007 / 0.004 (119), FSR shading_change_pyramid 0.007 / 0.003 (119), FSR shading_change 0.006 / 0.003 (119), FSR prepare_reactivity 0.008 / 0.003 (119), FSR luma_instability 0.006 / 0.003 (119), FSR accumulate 0.007 / 0.003 (119), write back 0.019 / 0.005 (119), other group 0.006 / 0.000 (119)


### The frame's way out (`ppcopy.py`): the OpenGL queue at the present

- photon-taa canopy: Perfect Presentation's copy (to the fence): none
- photon-taa canopy: blit to the window (to SwapBuffers): 120 frames, GL queue row median 0.030 ms (p10 0.029, p90 0.032); GR active during it 33% (39 samples)
- photon-taa moving: Perfect Presentation's copy (to the fence): none
- photon-taa moving: blit to the window (to SwapBuffers): 120 frames, GL queue row median 0.030 ms (p10 0.029, p90 0.036); GR active during it 30% (48 samples)
- dlaa-default canopy: Perfect Presentation's copy (to the fence): none
- dlaa-default canopy: blit to the window (to SwapBuffers): 120 frames, GL queue row median 0.027 ms (p10 0.026, p90 0.030); GR active during it 29% (26 samples)
- dlaa-default moving: Perfect Presentation's copy (to the fence): none
- dlaa-default moving: blit to the window (to SwapBuffers): 120 frames, GL queue row median 0.026 ms (p10 0.025, p90 0.038); GR active during it 27% (40 samples)
- fsr3 canopy: Perfect Presentation's copy (to the fence): none
- fsr3 canopy: blit to the window (to SwapBuffers): 120 frames, GL queue row median 0.027 ms (p10 0.027, p90 0.028); GR active during it 35% (32 samples)
- fsr3 moving: Perfect Presentation's copy (to the fence): none
- fsr3 moving: blit to the window (to SwapBuffers): 120 frames, GL queue row median 0.026 ms (p10 0.025, p90 0.027); GR active during it 30% (29 samples)

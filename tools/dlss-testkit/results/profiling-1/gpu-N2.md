Session start on the performance counter: 6736904.7 ms. Render thread 281650801963220.

| mode | viewpoint | frames | frame period ms | blocked in SwapBuffers ms | glClientWaitSync+glFinish ms | GPU busy ms (GL work, union) | GPC MHz | GR active % | Tensor active % |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| dlaa-default | canopy | 119 | 10.31 | no SwapBuffers |0.00 | 6.61 | 2713 | 58 | 1.8 |

### GPU ms per frame by part (median / mean over the frames)

**dlaa-default, canopy** (119 frames, each from one copy inputs push to the next; GL GPU work summed 6.61 ms)

| part | GPU ms median | GPU ms mean |
| --- | --- | --- |
| rest of the frame | 6.151 | 5.861 |
| copy inputs: last copy's row, runs on to the write back (holds the DirectX gap) | 0.000 | 0.261 |
| copy inputs | 0.161 | 0.160 |
| write back | 0.085 | 0.085 |
| (rest of the frame: before the Bridge) | 0.000 | |
| (rest of the frame: after the Bridge) | 6.151 | |
| DLSS on DirectX + handover: the GL queue's gap from the last input copy to the write back | 1.043 (p10 1.020, p90 1.116) | |

During the DirectX gap: GR active 94%, Tensor active 18.6% (1259 samples).
Write back's draw blocked the CPU for more than 1 ms in 30 of 119 frames (median 4.56 ms when it did). GPU idle after the write back until the next GL work: median 0.001 ms (0.771 ms in frames with the block, 0.001 ms without).

GL calls that held the render thread for more than 0.2 ms (mean ms per frame, number of such calls): glBindFramebuffer 1.21 (44), glDrawArrays 1.15 (30), glClear 0.79 (31), glCopyImageSubData 0.23 (8), glCopyTexSubImage2D 0.18 (10), glDrawElements 0.08 (3), glDrawTextureNV 0.07 (2), glMultiDrawElementsBaseVertex 0.03 (1), glClearTexImage 0.02 (1), glFenceSync 0.01 (1)

Rest of the frame by the GL call its GPU rows start at (mean ms per frame): glDrawElements 1.89, glMultiDrawElementsBaseVertex 1.46, glCopyImageSubData 1.45, glBindFramebuffer 0.38, glClear 0.25, glCopyTexSubImage2D 0.23, glGenerateTextureMipmap 0.18

CPU side of the groups (median ms: whole group / its longest single call; count):
copy inputs 0.016 / 0.003 (119), evaluate (DirectX) 0.405 / 0.000 (119), write back 0.033 / 0.010 (119), other group 0.005 / 0.000 (119)


### The frame's way out (`ppcopy.py`): the OpenGL queue at the present

- dlaa-default canopy: Perfect Presentation's copy (to the fence): 120 frames, GL queue row median 1.461 ms (p10 0.049, p90 2.711); GR active during it 14% (1405 samples)
- dlaa-default canopy: blit to the window (to SwapBuffers): none

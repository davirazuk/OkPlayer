/* Preview stubs for the Vita SDK headers main.c includes. */
#ifndef PREVIEW_PSP2_H
#define PREVIEW_PSP2_H

#include <stdint.h>

typedef uint32_t SceUInt32;
typedef int SceUID;
typedef unsigned int SceSize;

enum {
    SCE_CTRL_SELECT = 1 << 0, SCE_CTRL_START = 1 << 3, SCE_CTRL_UP = 1 << 4, SCE_CTRL_RIGHT = 1 << 5,
    SCE_CTRL_DOWN = 1 << 6, SCE_CTRL_LEFT = 1 << 7, SCE_CTRL_LTRIGGER = 1 << 8, SCE_CTRL_RTRIGGER = 1 << 9,
    SCE_CTRL_TRIANGLE = 1 << 12, SCE_CTRL_CIRCLE = 1 << 13, SCE_CTRL_CROSS = 1 << 14, SCE_CTRL_SQUARE = 1 << 15,
};
enum { SCE_CTRL_MODE_ANALOG = 1 };
typedef struct { uint64_t timeStamp; unsigned int buttons; unsigned char lx, ly, rx, ry; } SceCtrlData;
static inline int sceCtrlSetSamplingMode(int m) { (void)m; return 0; }
static inline int sceCtrlPeekBufferPositive(int port, SceCtrlData *d, int n) { (void)port; (void)n; d->buttons = 0; return 1; }

enum { SCE_TOUCH_PORT_FRONT = 0, SCE_TOUCH_SAMPLING_STATE_START = 1 };
typedef struct { uint16_t x, y; } SceTouchReport;
typedef struct { uint64_t timeStamp; unsigned int status; unsigned int reportNum; SceTouchReport report[8]; } SceTouchData;
static inline int sceTouchSetSamplingState(int port, int s) { (void)port; (void)s; return 0; }
static inline int sceTouchPeek(int port, SceTouchData *d, int n) { (void)port; (void)n; d->reportNum = 0; return 1; }

enum { SCE_KERNEL_POWER_TICK_DISABLE_AUTO_SUSPEND = 1 };
static inline int sceKernelPowerTick(int t) { (void)t; return 0; }
static inline SceUInt32 sceKernelGetProcessTimeLow(void) { return 0; }

#endif

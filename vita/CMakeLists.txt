cmake_minimum_required(VERSION 3.10)

if(NOT DEFINED CMAKE_TOOLCHAIN_FILE)
  if(DEFINED ENV{VITASDK})
    set(CMAKE_TOOLCHAIN_FILE "$ENV{VITASDK}/share/vita.toolchain.cmake" CACHE PATH "toolchain file")
  else()
    message(FATAL_ERROR "Set VITASDK to your VitaSDK install")
  endif()
endif()

project(okplayer C)
include("$ENV{VITASDK}/share/vita.cmake" REQUIRED)

set(VITA_APP_NAME "okplayer")
set(VITA_TITLEID  "OKPL00001")
set(VITA_VERSION  "00.30")

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O2 -Wall -Wno-unused-function -std=gnu11")

# dr_flac, dr_mp3, dr_wav and stb_vorbis live here; fetch them with tools/fetch_decoders.sh.
include_directories(third_party)

add_executable(${PROJECT_NAME}
  src/main.c
  src/gfx.c
  src/library.c
  src/player.c
  src/decoders.c
)

target_link_libraries(${PROJECT_NAME}
  vita2d
  SceDisplay_stub
  SceGxm_stub
  SceSysmodule_stub
  SceCtrl_stub
  SceTouch_stub
  SceAudio_stub
  ScePgf_stub
  SceCommonDialog_stub
  SceAppUtil_stub
  SceAppMgr_stub
  SceIofilemgr_stub
  SceLibKernel_stub
  png
  jpeg
  z
  m
  c
)

vita_create_self(${PROJECT_NAME}.self ${PROJECT_NAME})
vita_create_vpk(${PROJECT_NAME}.vpk ${VITA_TITLEID} ${PROJECT_NAME}.self
  VERSION ${VITA_VERSION}
  NAME ${VITA_APP_NAME}
  FILE sce_sys/icon0.png sce_sys/icon0.png
  FILE sce_sys/livearea/contents/bg.png sce_sys/livearea/contents/bg.png
  FILE sce_sys/livearea/contents/startup.png sce_sys/livearea/contents/startup.png
  FILE sce_sys/livearea/contents/template.xml sce_sys/livearea/contents/template.xml
)

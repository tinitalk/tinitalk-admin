GRADLE_ARGS ?=
MIN_CLIENT_GRADLE_ARGS = $(GRADLE_ARGS) -PtinitalkAdminAbi=arm64

.PHONY: client client-min check clean

GRADLE_FLAGS ?= --no-daemon

ifeq ($(OS),Windows_NT)
SHELL := cmd.exe
.SHELLFLAGS := /C
RUN_GRADLE = gradlew.bat $(GRADLE_FLAGS) $(1)
CREATE_DIST = if not exist dist mkdir dist
COPY_CLIENT = copy /Y app\build\outputs\apk\debug\app-debug.apk dist\tinitalk-admin-debug.apk >NUL
COPY_CLIENT_MIN = copy /Y app\build\outputs\apk\release\app-release.apk dist\tinitalk-admin-min.apk >NUL
CLEAN_DIST = if exist dist rmdir /S /Q dist
else
SHELL := /bin/sh
CREATE_DIST = mkdir -p dist
COPY_CLIENT = cp app/build/outputs/apk/debug/app-debug.apk dist/tinitalk-admin-debug.apk
COPY_CLIENT_MIN = cp app/build/outputs/apk/release/app-release.apk dist/tinitalk-admin-min.apk
CLEAN_DIST = rm -rf dist
ifneq ($(WSL_DISTRO_NAME),)
WINDOWS_CMD ?= /mnt/c/Windows/System32/cmd.exe
WINDOWS_ROOT := $(shell wslpath -w "$(CURDIR)")
RUN_GRADLE = "$(WINDOWS_CMD)" /D /C "cd /D $(WINDOWS_ROOT) && gradlew.bat $(GRADLE_FLAGS) $(1)"
else
RUN_GRADLE = ./gradlew $(GRADLE_FLAGS) $(1)
endif
endif

client:
	@$(CREATE_DIST)
	@$(call RUN_GRADLE,testDebugUnitTest lintDebug assembleDebug)
	@$(COPY_CLIENT)

client-min:
	@$(CREATE_DIST)
	@$(call RUN_GRADLE,assembleRelease $(MIN_CLIENT_GRADLE_ARGS))
	@$(COPY_CLIENT_MIN)

check:
	@$(call RUN_GRADLE,testDebugUnitTest lintDebug assembleDebug assembleRelease)

clean:
	@$(call RUN_GRADLE,clean)
	@$(CLEAN_DIST)

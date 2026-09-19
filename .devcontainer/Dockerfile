# See here for image contents: https://github.com/devcontainers/images/tree/main/src/java

# [Choice] Java version: 25, 25-trixie, 25-bookworm
ARG VARIANT="25"
FROM mcr.microsoft.com/devcontainers/java:3-${VARIANT}

# [Option] Install Gradle
ARG INSTALL_GRADLE="false"
ARG GRADLE_VERSION=""
RUN if [ "${INSTALL_GRADLE}" = "true" ]; then su vscode -c "umask 0002 && . /usr/local/sdkman/bin/sdkman-init.sh && sdk install gradle \"${GRADLE_VERSION}\""; fi

ARG NODE_VERSION="24"
RUN if [ "${NODE_VERSION}" != "none" ]; then su vscode -c "umask 0002 && . /usr/local/share/nvm/nvm.sh && nvm install ${NODE_VERSION} 2>&1"; fi

ARG PNPM_VERSION="12.4.2"
RUN su vscode -c "umask 0002 && . /usr/local/share/nvm/nvm.sh && npm install -g pnpm@${PNPM_VERSION} 2>&1"

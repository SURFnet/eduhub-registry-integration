#!/usr/bin/env bash

# SPDX-FileCopyrightText: 2024 SURF B.V.
# SPDX-FileContributor: Joost Diepenmaat
# SPDX-FileContributor: Remco van 't Veer
#
# SPDX-License-Identifier: Apache-2.0

# Install clojure and babashka to `./bin` and `./lib`

set -ex

if [ ! -x "bin/clojure" ]; then
    curl -L -O https://github.com/clojure/brew-install/releases/latest/download/posix-install.sh
    bash posix-install.sh -p "$(pwd)"
fi

#!/usr/bin/env bash
set -euo pipefail

mvn -B -ntp -f backend/pom.xml verify
npm --prefix frontend install
npm --prefix frontend run check

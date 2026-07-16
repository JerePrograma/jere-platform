$ErrorActionPreference = "Stop"

mvn -B -f backend/pom.xml verify
npm --prefix frontend install
npm --prefix frontend run check
npm --prefix frontend run build

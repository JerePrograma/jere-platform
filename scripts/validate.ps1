$ErrorActionPreference = "Stop"

mvn -B -f backend/pom.xml verify
npm --prefix frontend ci
npm --prefix frontend run check
npm --prefix frontend run build

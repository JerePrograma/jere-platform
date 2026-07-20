$ErrorActionPreference = "Stop"

mvn -B -f backend/pom.xml verify
if ($LASTEXITCODE) { exit $LASTEXITCODE }

npm --prefix frontend ci
if ($LASTEXITCODE) { exit $LASTEXITCODE }

npm --prefix frontend run check
if ($LASTEXITCODE) { exit $LASTEXITCODE }

npm --prefix frontend run build
if ($LASTEXITCODE) { exit $LASTEXITCODE }

$ErrorActionPreference = "Stop"

mvn -B -ntp -f backend/pom.xml verify
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

npm --prefix frontend install
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

npm --prefix frontend run check
exit $LASTEXITCODE

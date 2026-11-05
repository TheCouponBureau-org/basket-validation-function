@echo off
setlocal

where npm >nul 2>nul
if errorlevel 1 (
    echo npm was not found. Install Node.js and add npm to PATH.
    exit /b 1
)

where dotnet >nul 2>nul
if errorlevel 1 (
    echo The .NET SDK was not found. Install the .NET SDK and add dotnet to PATH.
    exit /b 1
)

npm ci
if errorlevel 1 exit /b 1
npm run build-rules
if errorlevel 1 exit /b 1

dotnet build TcbBasketValidation.Client.csproj --configuration Release
if errorlevel 1 exit /b 1

echo.
echo Created bin\Release\net45\TcbBasketValidation.Client.dll
echo All runtime dependencies are embedded in the DLL.

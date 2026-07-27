@rem Gradle wrapper for Windows
@echo off
setlocal

set DIRNAME=%~dp0
set APP_HOME=%DIRNAME%
set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

if defined JAVA_HOME (
    set JAVA_EXE=%JAVA_HOME%\bin\java.exe
) else (
    set JAVA_EXE=java
)

"%JAVA_EXE%" %JAVA_OPTS% %GRADLE_OPTS% ^
    "-Dorg.gradle.appname=gradlew" ^
    -classpath "%CLASSPATH%" ^
    org.gradle.wrapper.GradleWrapperMain %*

:end
endlocal

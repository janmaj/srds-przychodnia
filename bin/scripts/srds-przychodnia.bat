@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem
@rem SPDX-License-Identifier: Apache-2.0
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  srds-przychodnia startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%..

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and SRDS_PRZYCHODNIA_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="--add-opens" "java.base/jdk.internal.misc=ALL-UNNAMED" "--add-opens" "java.base/java.nio=ALL-UNNAMED" "--add-opens" "java.base/java.security=ALL-UNNAMED" "--add-opens" "jdk.unsupported/sun.misc=ALL-UNNAMED" "--add-opens" "java.base/sun.security.action=ALL-UNNAMED" "--add-opens" "jdk.naming.rmi/com.sun.jndi.rmi.registry=ALL-UNNAMED" "--add-opens" "java.base/sun.net=ALL-UNNAMED" "--add-opens" "java.base/java.lang=ALL-UNNAMED" "--add-opens" "java.base/java.lang.reflect=ALL-UNNAMED" "--add-opens" "java.base/sun.nio.ch=ALL-UNNAMED"

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH. 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME% 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\lib\srds-przychodnia.jar;%APP_HOME%\lib\cassandra-all-3.11.0.jar;%APP_HOME%\lib\logback-classic-1.5.16.jar;%APP_HOME%\lib\cassandra-driver-core-3.3.0.jar;%APP_HOME%\lib\metrics-jvm-3.1.0.jar;%APP_HOME%\lib\reporter-config3-3.0.3.jar;%APP_HOME%\lib\metrics-core-3.2.2.jar;%APP_HOME%\lib\cassandra-thrift-3.11.0.jar;%APP_HOME%\lib\log4j-over-slf4j-1.7.7.jar;%APP_HOME%\lib\jcl-over-slf4j-1.7.7.jar;%APP_HOME%\lib\thrift-server-0.3.7.jar;%APP_HOME%\lib\libthrift-0.9.2.jar;%APP_HOME%\lib\ohc-core-0.4.4.jar;%APP_HOME%\lib\reporter-config-base-3.0.3.jar;%APP_HOME%\lib\slf4j-api-2.0.16.jar;%APP_HOME%\lib\logback-core-1.5.16.jar;%APP_HOME%\lib\netty-handler-4.0.47.Final.jar;%APP_HOME%\lib\guava-19.0.jar;%APP_HOME%\lib\jnr-posix-3.0.27.jar;%APP_HOME%\lib\jnr-ffi-2.0.7.jar;%APP_HOME%\lib\snappy-java-1.1.1.7.jar;%APP_HOME%\lib\lz4-1.3.0.jar;%APP_HOME%\lib\compress-lzf-0.8.4.jar;%APP_HOME%\lib\commons-cli-1.1.jar;%APP_HOME%\lib\httpclient-4.2.5.jar;%APP_HOME%\lib\commons-codec-1.9.jar;%APP_HOME%\lib\commons-lang3-3.1.jar;%APP_HOME%\lib\commons-math3-3.2.jar;%APP_HOME%\lib\concurrentlinkedhashmap-lru-1.4.jar;%APP_HOME%\lib\antlr-3.5.2.jar;%APP_HOME%\lib\ST4-4.0.8.jar;%APP_HOME%\lib\antlr-runtime-3.5.2.jar;%APP_HOME%\lib\jackson-mapper-asl-1.9.2.jar;%APP_HOME%\lib\jackson-core-asl-1.9.2.jar;%APP_HOME%\lib\json-simple-1.1.jar;%APP_HOME%\lib\high-scale-lib-1.0.6.jar;%APP_HOME%\lib\snakeyaml-1.12.jar;%APP_HOME%\lib\jbcrypt-0.3m.jar;%APP_HOME%\lib\stream-2.5.2.jar;%APP_HOME%\lib\jna-4.4.0.jar;%APP_HOME%\lib\jamm-0.3.0.jar;%APP_HOME%\lib\netty-all-4.0.44.Final.jar;%APP_HOME%\lib\joda-time-2.4.jar;%APP_HOME%\lib\sigar-1.6.4.jar;%APP_HOME%\lib\ecj-4.4.2.jar;%APP_HOME%\lib\caffeine-2.2.6.jar;%APP_HOME%\lib\jctools-core-1.2.1.jar;%APP_HOME%\lib\netty-codec-4.0.47.Final.jar;%APP_HOME%\lib\netty-transport-4.0.47.Final.jar;%APP_HOME%\lib\netty-buffer-4.0.47.Final.jar;%APP_HOME%\lib\jffi-1.2.10.jar;%APP_HOME%\lib\jffi-1.2.10-native.jar;%APP_HOME%\lib\asm-commons-5.0.3.jar;%APP_HOME%\lib\asm-analysis-5.0.3.jar;%APP_HOME%\lib\asm-util-5.0.3.jar;%APP_HOME%\lib\asm-tree-5.0.3.jar;%APP_HOME%\lib\asm-5.0.3.jar;%APP_HOME%\lib\jnr-x86asm-1.0.2.jar;%APP_HOME%\lib\jnr-constants-0.9.0.jar;%APP_HOME%\lib\hibernate-validator-4.3.0.Final.jar;%APP_HOME%\lib\disruptor-3.0.1.jar;%APP_HOME%\lib\fastutil-6.5.7.jar;%APP_HOME%\lib\httpcore-4.2.4.jar;%APP_HOME%\lib\hppc-0.5.4.jar;%APP_HOME%\lib\jflex-1.6.0.jar;%APP_HOME%\lib\snowball-stemmer-1.3.0.581.1.jar;%APP_HOME%\lib\concurrent-trees-2.4.0.jar;%APP_HOME%\lib\netty-common-4.0.47.Final.jar;%APP_HOME%\lib\validation-api-1.0.0.GA.jar;%APP_HOME%\lib\jboss-logging-3.1.0.CR2.jar;%APP_HOME%\lib\commons-logging-1.1.1.jar;%APP_HOME%\lib\ant-1.7.0.jar;%APP_HOME%\lib\ant-launcher-1.7.0.jar


@rem Execute srds-przychodnia
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %SRDS_PRZYCHODNIA_OPTS%  -classpath "%CLASSPATH%" cassdemo.Main %*

:end
@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
rem Set variable SRDS_PRZYCHODNIA_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%SRDS_PRZYCHODNIA_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega

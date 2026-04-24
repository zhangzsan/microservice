@echo off
chcp 65001 >nul

set SKYWALKING_AGENT_PATH=D:\Develop\skywalking-agent
set SKYWALKING_COLLECTOR=127.0.0.1:11800

cd /d %~dp0storage-service

mvn spring-boot:run -Dspring-boot.run.jvmArguments="-javaagent:%SKYWALKING_AGENT_PATH%\skywalking-agent.jar -Dskywalking.agent.service_name=storage-service -Dskywalking.collector.backend_service=%SKYWALKING_COLLECTOR% -Xms512m -Xmx1024m"

@echo off
rem ============================================================
rem  start-backend.example.bat
rem  后端启动模板（无敏感凭据版本，可入库）。
rem  复制为 start-backend.bat 后填写真实 MYSQL_PASSWORD 再使用。
rem  start-backend.bat 含数据库密码，已被 .gitignore 排除，禁止提交仓库。
rem ============================================================
cd /d D:\jshz-PMO\backend
set MYSQL_HOST=127.0.0.1
set MYSQL_PORT=3306
set MYSQL_DB=expert_system
set MYSQL_USER=root
set MYSQL_PASSWORD=<请填写数据库密码>
java -jar target\expert-system-1.001.jar

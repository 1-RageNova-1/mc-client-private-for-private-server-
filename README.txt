MaiClient 1.8.9 - Windows package

REQUIREMENTS: Java 21 (adoptium.net), Node 18+ (nodejs.org), optional PostgreSQL

QUICK START
1) API:  cd api
         set JWT_SECRET=change_me_long_random_16plus
         set MEMORY_DB=1
         node src\standalone.js
2) Client: cd client
           set MAICLIENT_API=http://127.0.0.1:8787
           launcher-maiclient.bat
3) Sign in with REAL Microsoft account in Account Manager.

See docs\WINDOWS.md and docs\POSTGRES.md for production.
Real Microsoft auth only. Original Adjust jar in client\original-reference\

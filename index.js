const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = 3000;

const server = http.createServer((req, res) => {
    if (req.url === '/' || req.url === '/index.html') {
        fs.readFile(path.join(__dirname, 'index.html'), 'utf8', (err, data) => { // 'utf8' 인코딩 추가
            if (err) {
                res.writeHead(500, { 'Content-Type': 'text/plain; charset=UTF-8' });
                res.end('Internal Server Error');
            } else {
                res.writeHead(200, { 'Content-Type': 'text/html; charset=UTF-8' }); // charset 추가
                res.end(data);
            }
        });
    } else {
        res.writeHead(404, { 'Content-Type': 'text/plain; charset=UTF-8' }); // 404 페이지도 UTF-8 설정
        res.end('404 Not Found');
    }
});

server.listen(PORT, () => {
    console.log(`Server is running at http://localhost:${PORT}`);
});


/*
const express = require("express");
const app = express();

app.get("/", (req, res) => {
    res.send("<h1>동적으로 생성된 HTML!</h1>");
});

app.listen(3000, () => {
    console.log("서버 실행 중: http://localhost:3000");
});*/
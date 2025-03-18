const WebSocket = require("ws");

// WebSocket 서버 생성 (포트 8080 사용)
const wss = new WebSocket.Server({ port: 8080 });

console.log("WebSocket 서버가 ws://localhost:8080 에서 실행 중!");

wss.on("connection", (ws) => {
    console.log("클라이언트가 연결됨!");

    // 연결된 클라이언트에게 초기 메시지 전송
    ws.send("서버에 연결되었습니다!");

    // 클라이언트로부터 메시지를 받을 때
    ws.on("message", (message) => {
        console.log(`클라이언트로부터 받은 메시지: ${message}`);

        // 클라이언트에게 다시 메시지 전송
        ws.send(`서버에서 받은 메시지: ${message}`);
    });

    // 연결 종료 시
    ws.on("close", () => {
        console.log("클라이언트가 연결을 종료함!");
    });

    // 에러 처리
    ws.on("error", (error) => {
        console.error("WebSocket 오류:", error);
    });
});

import 'dart:io';

import 'package:arcore_flutter_plugin/arcore_flutter_plugin.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  await SystemChrome.setPreferredOrientations([
    DeviceOrientation.landscapeLeft,
    DeviceOrientation.landscapeRight,
  ]);

  await requestAllPermissions();

  runApp(App());
}

Future<void> requestAllPermissions() async {
  final Map<Permission, String> permissions = {
    Permission.bluetoothScan: '근처 블루투스 기기',
    Permission.bluetoothConnect: '블루투스 연결',
    Permission.location: '위치',
    Permission.camera: '카메라',
    Permission.microphone: '마이크',
    Permission.photos: '사진 (Android 13+)',
    Permission.videos: '동영상 (Android 13+)',
  };

  for (var entry in permissions.entries) {
    final status = await entry.key.request();
    debugPrint('${entry.value} 권한 상태: $status');
  }

  // 추가 확인: 거부된 권한이 있다면 설정창 안내
  if (await Permission.camera.isPermanentlyDenied ||
      await Permission.microphone.isPermanentlyDenied ||
      await Permission.location.isPermanentlyDenied) {
    openAppSettings(); // 설정창으로 유도
  }
}

class App extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      theme: ThemeData.dark(),
      home: ARViewScreen(),
    );
  }
}


class ARViewScreen extends StatefulWidget {
  @override
  _ARViewScreenState createState() => _ARViewScreenState();
}

class _ARViewScreenState extends State<ARViewScreen> {
  static const platform = MethodChannel('arcore_channel');

  Future<void> adjustFocus() async {
    try {
      final String result = await platform.invokeMethod('adjustStereoFocus');
      print("테스트 성공: $result");
    } on PlatformException catch (e) {
      print("테스트 실패: ${e.message}");
    }
  }

  Future<void> connectToPC() async {
    try {
      final String result = await platform.invokeMethod('connectToPC');
      print("서버 연결 성공: $result");
    } on PlatformException catch (e) {
      print("서버 연결 실패: $e");
    }
  }

  @override
  void dispose() {
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              ElevatedButton(
                onPressed: adjustFocus,
                child: Text("테스트"),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
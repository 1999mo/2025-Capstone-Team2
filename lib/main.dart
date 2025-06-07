import 'dart:io';

import 'package:arcore_flutter_plugin/arcore_flutter_plugin.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  await SystemChrome.setPreferredOrientations([
    DeviceOrientation.landscapeLeft,
    DeviceOrientation.landscapeRight,
  ]);

  runApp(App());
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